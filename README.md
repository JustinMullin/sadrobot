# Sad Robot MTG Card Fetcher

Sad Robot is a fully-featured Magic the Gathering card fetcher chatbot. Currently Slack integration
is fully supported, including self-service installation from the Slack apps directory. Discord integration is
supported by the bot, but Discord app installation is not currently available.

## Configuration

### Provisioning the SQLite Database

Sad Robot requires a SQLite database to store and retrieve client tokens for authorizing with chat
workspaces. To set up and configure this, first install SQLite 3.x from [https://www.sqlite.org/download.html].
Then, run the following command to configure the schema:
`sqlite3 Tokens.sqlite ".read schema.sql"`

This should create an empty table called `authenticated_workspaces` where workspace credentials can
be added.

### Manually Adding Workspaces (optional)

Workspaces can then be added manually to the database directly, e.g.:

```
sqlite3 Tokens.sqlite "insert into authenticated_workspaces (enabled, workspaceType, token, botToken, botUserId, name, workspaceId) values (true, 'Slack', 'abc-def', 'abc-def', 'abc-def', 'Workspace Name', 'workspace-abc');"
```

## Running the Application

To run the application, simply execute:
`./gradlew run`

Optionally, to build an executable JAR for distribution/deployment:
`./gradlew shadowJar`

An executable JAR with all dependencies will be exported to `build/lib/sadrobot-[x.y]-all.jar`.

### Environment Variables

The following optional environment variables can be set when executing the application to configure
additional functionality:

#### Slack OAuth Self-service Installation

If you wish to configure an OAuth flow so users can install their workspaces automatically via Slack, the following
environment variables should be set, matching your application configuration in the Slack developer console:

```
SLACK_CLIENT_ID=[clientId]
SLACK_CLIENT_SECRET=[clientSecret]
OAUTH_REDIRECT_URL=[url.to.this.application/auth]
```

Note: the above redirect URL expects this application is deployed at a publicly accessible location!

#### Google Analytics Integration

Sad Robot is also configured to send analytics events via the Google Analytics SDK. If you wish to
enable this functionality, set the following environment variable:

```
GOOGLE_ANALYTICS_TRACKING_ID=[trackingId]
```
