export default {
    default: {
        paths: ["features/**/*.feature"],
        import: [
            "steps/**/*.js",
            "support/**/*.js"
        ],
        format: ["progress"]
    }
};