package com.getpcpanel.integration.display;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class DdcOffCodeTest {
    @Test
    void standbyOffWhenTheMonitorListsIt() {
        // A Samsung lists 05 but ignores it; standby off works on it and on monitors that also obey 05.
        assertEquals(4, DdcOffCode.choose("(vcp(02 10 D6(01 04 05)))"));
    }

    @Test
    void powerOffOnlyWhenTheMonitorListsNoStandbyOff() {
        assertEquals(5, DdcOffCode.choose("(vcp(02 10 D6(01 05)))"));
    }

    @Test
    void standbyOffOtherwise() {
        assertEquals(4, DdcOffCode.choose("(vcp(D6(01 04)))"));
        assertEquals(4, DdcOffCode.choose(null));
        assertEquals(4, DdcOffCode.choose("(vcp(10 12))"));
    }

    @Test
    void readsOnlyTheRawCapabilitiesInDdcutilOutput() {
        var verbose = """
                Model: FALCON
                VCP Features:
                   Feature: D6 (Power mode)
                      Values:
                         01: DPM: On,  DPMS: Off
                         05: Write only value to turn off display
                Unparsed capabilities string: (prot(monitor)type(lcd)vcp(02 10 D6(01 04))mccs_ver(2.1))
                """;
        assertEquals(4, DdcOffCode.choose(verbose), "the parsed list above the raw string does not count");
        assertEquals(5, DdcOffCode.choose(verbose.replace("D6(01 04)", "D6(01 05)")));
        assertEquals(4, DdcOffCode.choose("Feature: D6 (05)\n"), "no raw vcp section");
    }

    @Test
    void readsThePowerMode() {
        assertEquals(Boolean.TRUE, DdcOffCode.isOn(1));
        assertEquals(Boolean.FALSE, DdcOffCode.isOn(4));
        assertEquals(Boolean.FALSE, DdcOffCode.isOn(5));
        assertNull(DdcOffCode.isOn(0), "some monitors answer 0 whatever their state");
    }

    @Test
    void readsOnlyThePowerModeValues() {
        assertEquals(4, DdcOffCode.choose("(prot(monitor)vcp(14(05 08) D6(01 04) DF))"), "05 belongs to another code");
        assertEquals(4, DdcOffCode.choose("(vcp(AD6(01 05) D6(01 04)))"), "AD6 is not D6");
        assertEquals(5, DdcOffCode.choose("(prot(monitor)type(lcd)vcp(02 04 60( 01 03 11) d6( 01 05 ) DF)mccs_ver(2.1))"));
    }
}
