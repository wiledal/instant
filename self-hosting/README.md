# Self Hosting InstantDB

Documentation for self hosting is available at [www.instantdb.com/docs/self-hosting](https://instantdb.com/docs/self-hosting)

See [UPGRADE_DB.md](UPGRADE_DB.md) for instructions on upgrading existing backends from postgres 16 to 17.

See [POSTAL.md](POSTAL.md) to configure this fork to send email through a
self-hosted Postal server and preserve signing keys across VM replacements.

## Run without file storage

This fork can run without S3 credentials. From this directory, run:

```sh
docker compose -f docker-compose.no-storage.yml up --build -d
```

This builds the patched backend from this checkout and starts Postgres and the
dashboard. The backend is at http://localhost:8888 and the dashboard is at
http://localhost:3000. It does not start MinIO or require an S3 key.

When running the backend directly, file storage is disabled automatically when
neither the config file nor `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY`
provide a complete credential pair. Blank or incomplete pairs leave it disabled.
Database queries, authentication, and realtime sync can run without file storage.
Storage operations return a descriptive validation error. S3 cleanup and backup
workers do not start, and new WAL history is stored in Postgres.

Set `INSTANT_STORAGE_ENABLED=false` to disable storage even with credentials.
Set `INSTANT_STORAGE_ENABLED=true` to enable it with an AWS profile or IAM role
through the AWS SDK's default credential provider. Enabling storage still requires
valid S3 credentials, a bucket, and the appropriate permissions.

The existing Compose files still provide local file storage through MinIO.
