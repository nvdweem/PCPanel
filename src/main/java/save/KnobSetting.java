package save;

public class KnobSetting {
    private int minTrim;
    private int maxTrim = 100;
    private boolean logarithmic;
    private int buttonDebounce = 50;

    public int getMinTrim() {
        return minTrim;
    }

    public void setMinTrim(int minTrim) {
        this.minTrim = minTrim;
    }

    public int getMaxTrim() {
        return maxTrim;
    }

    public void setMaxTrim(int maxTrim) {
        this.maxTrim = maxTrim;
    }

    public boolean isLogarithmic() {
        return logarithmic;
    }

    public void setLogarithmic(boolean logarithmic) {
        this.logarithmic = logarithmic;
    }

    public int getButtonDebounce() {
        return buttonDebounce;
    }

    public void setButtonDebounce(int buttonDebounce) {
        this.buttonDebounce = buttonDebounce;
    }
}

