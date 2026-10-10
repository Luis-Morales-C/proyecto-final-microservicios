import { setWorldConstructor } from "@cucumber/cucumber";

class CustomWorld {
    constructor() {
        this.baseUrl =
            process.env.BASE_URL ??
            "http://localhost:8080";

        this.token = null;
        this.response = null;
        this.responseBody = null;
    }
}

setWorldConstructor(CustomWorld);