import { When, Then } from "@cucumber/cucumber";
import assert from "node:assert";

When(
    "consulto el API Gateway",
    async function () {
        this.response =
            await fetch(this.baseUrl);
    }
);

Then(
    "el sistema debe responder",
    function () {
        assert.ok(this.response);
    }
);