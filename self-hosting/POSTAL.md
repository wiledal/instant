# Postal for self-hosted Instant

This fork supports Postal's HTTP API. The official Instant server image does
not include this change; build the backend with `server/Dockerfile.self-hosted`
from this fork before configuring the provider.

## Current deployment prerequisites

The VM at `91.98.140.163` has outbound port 587 connectivity, but connections
to two mail servers on port 25 timed out on October 13, 2026. Postal needs
outbound port 25 for direct mail delivery. Hetzner blocks this port by default.
Request an unblock using the account's limits request form, then retest from
the VM before proceeding. A successful SMTP greeting verifies connectivity.

```sh
timeout 10 bash -c 'exec 3<>/dev/tcp/gmail-smtp-in.l.google.com/25; head -n 1 <&3'
```

The proposed hostnames are `postal.on.hugos.computer` for the HTTPS web/API
endpoint and `mail.hugos.computer` for SMTP. DNS is managed by Vercel. Configure
the sending IP's reverse DNS through Hetzner to match the SMTP hostname.
Postal's installation requires MariaDB and persistent configuration and data.
Follow the official installation guide and use Traefik in Dokploy for HTTPS.

## Connect Instant

Create a Postal organization, mail server and verified sending domain for
`hugos.computer`. Create an API credential for that mail server, then configure
these variables in Instant's backend container environment:

```dotenv
INSTANT_EMAIL_PROVIDER=postal
POSTAL_API_URL=https://postal.on.hugos.computer
POSTAL_API_KEY=replace-with-the-postal-api-credential
INSTANT_DASHBOARD_EMAIL_SENDER_NAME=Instant
INSTANT_DASHBOARD_EMAIL_SENDER_EMAIL=verify@hugos.computer
INSTANT_APP_EMAIL_SENDER_NAME=Instant
INSTANT_APP_EMAIL_SENDER_EMAIL=verify@hugos.computer
INSTANT_TEAM_EMAIL_SENDER_NAME=Instant
INSTANT_TEAM_EMAIL_SENDER_EMAIL=verify@hugos.computer
```

These variables must be passed into the server container by Compose; placing
them in Dokploy's environment without referencing them in Compose is not enough.
`POSTAL_API_URL` is the base URL, without `/api/v1`. Postal must return both an
HTTP success status and a JSON `status` of `success`. Redirects are disabled so
API credentials cannot be forwarded to another endpoint.

Publish the domain's actual DKIM selector and public key from Postal in Vercel
DNS. Merge SPF settings with any existing SPF record instead of adding a second
one. Configure the return path and DMARC after validating SPF and DKIM alignment.
Send a test login email and inspect the receiving mailbox's authentication
results before treating delivery as working.

## Recover on another VM

Keep a recovery archive in the personal 1Password account, outside the VM.
It must contain:

- A consistent MariaDB dump of Postal's main and per-mail-server databases.
  Per-domain DKIM private keys are in the database.
- Postal's configuration and `signing.key`.
- The deployed Compose definition, image versions and required secrets.
- A copy of the DNS records and instructions for restoring them.

Take another recovery snapshot after changing domains, DKIM keys, credentials,
or configuration. A single initial archive does not protect later changes or
queued messages. For regular full database backups, use encrypted off-VM storage;
1Password can keep the recovery secret and a small configuration archive.

Restore the databases and configuration before launching Postal on a replacement
VM. Keep the same signing keys and selectors, and update IP-dependent DNS and
reverse DNS. Test an actual restore before relying on this procedure. If all
copies of a private key are lost, generate a new key and publish its public key
under a new selector; the old key cannot be reconstructed from DNS.

## References

- [Postal prerequisites](https://docs.postalserver.io/getting-started/prerequisites/)
- [Postal installation](https://docs.postalserver.io/getting-started/installation/)
- [Postal sending domains](https://docs.postalserver.io/features/sending-domains/)
- [Postal API](https://apiv1.postalserver.io/controllers/send/message.html)
- [Hetzner mail port restrictions](https://docs.hetzner.com/cloud/servers/faq/#why-can-i-not-send-any-mails-from-my-server)
