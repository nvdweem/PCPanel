package com.getpcpanel;

import static io.restassured.RestAssured.given;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.http.ContentType;

@QuarkusIntegrationTest
public class NativeTestIT {
    @Test
    public void testOverlay() throws InterruptedException {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "value": 123,
                            "icon": "test-icon"
                        }
                        """)
                .when().get("/api/overlay").then().statusCode(200);
        System.out.println("Test?!?!");
        Thread.sleep(50_000);
    }
}
