module.exports = {
    root: true,
    env: {
        browser: true,
        es2021: true,
    },
    extends: [
        'eslint:recommended',
    ],
    parserOptions: {
        ecmaVersion: 2022,
        sourceType: 'module',
    },
    ignorePatterns: [
        'dist/',
        'playwright.config.js',
    ],
    rules: {
        'no-console': process.env.NODE_ENV === 'production' ? 'warn' : 'off',
        // Legacy inline scripts still contain placeholders; keep these disabled until
        // the migration to screen modules is complete.
        'no-unused-vars': 'off',
        'no-empty': 'off',
    },
};
