# FlowStudio login UI update

This package keeps the existing Java backend, authentication endpoints, task runner flow, and post-login editor page unchanged. The only functional changes are in `index.html`, `script.js`, and `styles.css`.

The same modified frontend files are placed in both project root and `backend/src/main/resources/static/`.

## Required video assets

Put these files in `assets/` for static hosting and in `backend/src/main/resources/static/assets/` for Spring Boot hosting:

- `9_src.mp4`
- `9_ours.mp4`
- `8_src.mp4`
- `8_ours.mp4`
- `7_src.mp4`
- `7_ours.mp4`

The login page loads them with relative URLs like `assets/9_src.mp4`.
