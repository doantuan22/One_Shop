# OneShop AWS class demo

This runbook is for the course project only. It publishes the current demo catalog and synthetic accounts from `database/seed/20_demo_data.sql`; it is not a production setup and must not contain real customer information. The seed gives the demo accounts one shared password chosen when generating its BCrypt hash.

## Layout and cost controls

- One Amazon Linux 2023 EC2 `t3.small` serves the Spring app behind Nginx. It has 2 GiB RAM, an Elastic IP, encrypted 20 GiB gp3 disk, SSM access, and no inbound SSH.
- Amazon RDS SQL Server Express `db.t3.micro` uses encrypted 20 GiB gp3 storage, one AZ, one day of backups, and private subnets. Port 1433 accepts connections only from the app security group. This preserves the SQL Server engine used by the existing seed.
- There is no NAT Gateway, load balancer, or purchased domain. HTTPS uses a short-lived Let's Encrypt certificate for the Elastic IP.
- GitHub Actions uses OIDC credentials restricted to `doantuan22/One_Shop` on `main`. Release jars are immutable S3 objects and expire after 15 days.
- These EC2 and RDS instance classes are eligible for the AWS Free plan. Free plan access uses AWS credits and has service limits; it is not an unlimited free tier. The `db.t3.micro` SQL Server Express instance is intentionally small and suited to a light student demo, not production traffic. Other resources such as public IPv4, EBS/RDS storage, snapshots, data transfer, and taxes may still consume credits. Resource tags are reminders, not automatic expiry. Delete the stack before day 15 if the demo should stop then.

## 1. Create the AWS stack

After this PR is reviewed and merged, open CloudFormation in `ap-southeast-1` and create a stack from `deploy/aws/stack.yml` (the raw template URL is `https://raw.githubusercontent.com/doantuan22/One_Shop/main/deploy/aws/stack.yml`). Enter a unique RDS master password with at least 24 alphanumeric characters in the NoEcho field. Keep it in a password manager; you will enter it again into Parameter Store. Do not put it in GitHub, this repository, or a terminal command.

Wait for `CREATE_COMPLETE`. Save these outputs: `AppPublicIp`, `AppInstanceId`, `DatabaseEndpoint`, `ArtifactBucketName`, and `GitHubDeployRoleArn`. Confirm the instance appears as `Online` in Systems Manager Fleet Manager. If it is not online, stop before opening the deploy workflow; inspect EC2 cloud-init output and SSM status first.

The stack creates one OIDC provider and an IAM role that can upload release objects and run the deploy script as root on this single EC2 instance. Keep the role trust limited to the stated repository and `main` branch.

## 2. Store credentials in Systems Manager Parameter Store

In `ap-southeast-1`, create the following parameters. Choose **SecureString** and the default `aws/ssm` key for the four secret entries; use **String** for the two CloudFormation-created endpoint/name entries. Never send secret values in chat or commit them.

| Name | Type | Value |
| --- | --- | --- |
| `/oneshop/demo/rds-master-password` | SecureString | The same RDS master password entered into CloudFormation |
| `/oneshop/demo/app-db-password` | SecureString | A new unique 24+ character alphanumeric password for `oneshop_app` |
| `/oneshop/demo/seed-password-hash` | SecureString | BCrypt hash generated in step 3; it starts with `$2a$`, `$2b$`, or `$2y$` |
| `/oneshop/demo/app-env` | SecureString | The environment file shown below, with real values filled in |
| `/oneshop/demo/rds-endpoint` | String | Already created by CloudFormation |
| `/oneshop/demo/rds-master-username` | String | Already created by CloudFormation |

Use this environment file as the value for `/oneshop/demo/app-env`. Replace every bracketed placeholder. `DB_JDBC_OPTIONS` matches the truststore created on EC2; leave `DB_TRUST_SERVER_CERTIFICATE=false`.

```dotenv
SPRING_PROFILES_ACTIVE=prod
PORT=8080
DB_HOST=<DatabaseEndpoint output>
DB_PORT=1433
DB_NAME=oneshop
DB_USERNAME=oneshop_app
DB_PASSWORD=<value of app-db-password>
DB_TRUST_SERVER_CERTIFICATE=false
DB_JDBC_OPTIONS=;trustStore=/etc/oneshop/rds-truststore.jks;trustStorePassword=changeit
DB_POOL_SIZE=10
JWT_SECRET=<unique random value of at least 32 bytes>
JWT_COOKIE_SECURE=true
CLOUDINARY_CLOUD_NAME=<Cloudinary cloud name>
CLOUDINARY_API_KEY=<Cloudinary API key>
CLOUDINARY_API_SECRET=<Cloudinary API secret>
MAIL_HOST=
MAIL_PORT=587
MAIL_USERNAME=
MAIL_PASSWORD=
MAIL_FROM=
```

Do not enable online checkout or the mock payment gateway: this project has no real payment provider. Keep mail fields empty unless you configure a real mail provider.

## 3. Generate the shared demo-account password hash

On the Windows project checkout, use Java 21 and run:

```powershell
.\mvnw.cmd -DskipTests package
.\scripts\db\New-PasswordHash.ps1
```

The helper prompts without echoing the plain password and prints a BCrypt hash. Save that hash directly as `/oneshop/demo/seed-password-hash` SecureString. Remember the plain password privately: the seed assigns it to the demo accounts, including the seeded admin account. Do not reuse a real account password.

## 4. Accept the Microsoft tool terms and load the existing demo seed

The DB is private, so run the database bootstrap from the EC2 instance using Systems Manager Session Manager. Review the Microsoft software terms yourself before accepting them. In the SSM shell, install the command-line client (the package repository instructions are for RHEL 9; Amazon Linux 2023 compatibility is not explicitly listed by Microsoft):

```bash
sudo sh -c 'curl -fsSL https://packages.microsoft.com/config/rhel/9/prod.repo -o /etc/yum.repos.d/mssql-release.repo'
sudo env ACCEPT_EULA=Y dnf install -y mssql-tools18 unixODBC-devel
```

If installation fails on AL2023, stop here and use a supported SQL client that can run from this EC2 instance. Do not open database port 1433 to the internet as a workaround. After installation, load the bootstrap script from the merged `main` branch and run it:

```bash
sudo curl -fsSL https://raw.githubusercontent.com/doantuan22/One_Shop/main/scripts/db/bootstrap-demo-db.sh -o /tmp/bootstrap-demo-db.sh
sudo env PATH="/opt/mssql-tools18/bin:$PATH" bash /tmp/bootstrap-demo-db.sh
```

The bootstrap retrieves credentials from Parameter Store, runs the repository's existing `initialize-database.sh` in `demo` mode, loads `20_demo_data.sql` and its validation script, then creates/refreshes the least-privilege `oneshop_app` login. It refuses to merge the fixture into a database with a different catalog or existing users/stores.

## 5. Accept the certificate terms and enable HTTPS on the IP

This avoids domain purchase cost, but requires Certbot 5.4+ and Let's Encrypt's short-lived IP certificate profile. Review and personally accept the [Let's Encrypt Subscriber Agreement](https://letsencrypt.org/documents/LE-SA-v1.8-July-06-2026.pdf) before issuing. In the SSM shell, replace the IP and email, then run:

```bash
sudo /opt/certbot-venv/bin/certbot certonly \
  --preferred-profile shortlived \
  --webroot --webroot-path /var/www/certbot \
  --ip-address <AppPublicIp> \
  --agree-tos --non-interactive --no-eff-email \
  --email <your-email>
sudo /usr/local/sbin/enable-oneshop-https <AppPublicIp>
```

The certificate is valid for about 160 hours. The stack checks renewal every six hours and reloads Nginx after renewal; verify the timer with `systemctl list-timers certbot-renew.timer`. If the certificate cannot be issued or renewed, stop before enabling deployments that depend on HTTPS.

## 6. Enable GitHub Actions deployment last

In repository **Settings → Secrets and variables → Actions → Variables**, add these repository variables using the outputs from step 1:

- `AWS_DEPLOY_ROLE_ARN` = `GitHubDeployRoleArn`
- `AWS_DEPLOY_BUCKET` = `ArtifactBucketName`
- `AWS_DEPLOY_INSTANCE_ID` = `AppInstanceId`
- `AWS_DEPLOY_READY` = `true`

Set `AWS_DEPLOY_READY` last, after the RDS demo seed, app environment, and HTTPS are ready. A push to `main` then builds the jar and deploys through SSM. The workflow skips deployment while the readiness variable is absent. Review the Actions run and visit `https://<AppPublicIp>/`.

The deploy job builds with tests skipped to keep this course-demo pipeline fast; this PR does not add or run tests.

## 7. Stop billing after the demo

Before the intended 15-day window ends, delete the CloudFormation stack in `ap-southeast-1`. If stack deletion is blocked because the S3 artifact bucket is not empty, empty its `artifacts/` prefix first and retry. The RDS `DeletionPolicy: Snapshot` creates a final snapshot when the DB is removed; snapshots continue to incur storage charges, so delete that final snapshot later if you no longer need it. The existing `hotel-booking-sqlserver-snapshot` is not modified by this stack. Confirm the stack is gone and review Cost Explorer/Billing afterward.
