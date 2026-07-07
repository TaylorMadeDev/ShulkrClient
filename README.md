# Shulkr Client

Shulkr Client is a Fabric + ModernUI script workspace built around Minescript and a standalone Express backend.
It is a live scripting platform, not just a static demo: the client manages local scripts, backend-published scripts, templates, modules, and connected-client state.

## What You Get

- In-game UI shell with dashboard, scripts, editor, templates, WindowSpy, modules, settings, and about pages.
- Standalone backend in `backend/` for client presence, published scripts, templates, and admin visibility.
- Minescript integration for creating, installing, and running local scripts from the client.
- Backend health handling so the client can show `Server is offline` when the server is unavailable.

## Run It

```bash
./gradlew runClient
```

Then press `U` in-game.

## Web Client

The standalone browser client lives in `web-client/` and talks to the Express backend on `http://127.0.0.1:50991`.

```bash
cd backend
npm install
npm start

cd ../web-client
npm install
npm run dev
```

Open `http://127.0.0.1:5177`.

## Build It

```bash
./gradlew build
```

## Repo Notes

- Runtime data is generated under `run/`.
- The backend admin panel is served separately from the client.
- The web client is a separate frontend app and should not publish backend runtime data.
- Published scripts are managed through the backend, not hardcoded into the UI.

## License

This project inherits the Fabric example mod template's CC0 license.
