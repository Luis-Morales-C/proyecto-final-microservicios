package co.edu.uniquindio.apigateway.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class JwtPropertiesTest {
    @Test
    void gettersYSettersMantienenConfiguracion() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("secreto");
        properties.setIssuer("issuer");
        assertEquals("secreto", properties.getSecret());
        assertEquals("issuer", properties.getIssuer());
    }
}
