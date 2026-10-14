export default {
    paths: ["features/**/*.feature"],

    import: [
        "steps/**/*.js",
        "support/world.js"
    ],

    format: [
        "progress",
        "junit:reports/junit.xml",
        "json:reports/cucumber.json",
        "html:reports/cucumber.html"
    ]
};