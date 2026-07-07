# Shulkr Backend

Standalone Express backend for the Shulkr client UI.

```powershell
cd backend
npm install
npm start
```

Defaults:

- API: `http://127.0.0.1:50991`
- Scripts: `../run/minescript`
- Data: `../run/shulkr-backend`

Useful endpoints:

- `GET /api/health`
- `GET /api/scripts` for installed local editor scripts
- `POST /api/scripts` with `{ "name": "Example.py", "content": "..." }`
- `DELETE /api/scripts` with `{ "path": "Example.py" }`
- `GET /api/library/scripts` for published community scripts
- `POST /api/library/scripts` with `{ "name": "AutoFarm", "author": "EnderUser", "about": "...", "fileName": "AutoFarm.py", "code": "..." }`
- `POST /api/library/scripts/:id/install` to install a published script locally
- `DELETE /api/library/scripts/:id` to remove a published script
- `GET /api/templates`
- `POST /api/templates/use` with `{ "id": "autofarm-starter" }`
- `GET /api/modules/scripts` for local scripts flagged as reusable modules
- `PATCH /api/modules/scripts` with `{ "path": "pathfinding.pyj", "module": true }`
