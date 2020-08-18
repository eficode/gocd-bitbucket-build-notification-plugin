# GoCD Bitbucket build notification plugin

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)

GoCD plugin for updating build status on Bitbucket, 
based on [notification-skeleton-plugin](https://github.com/gocd-contrib/notification-skeleton-plugin).

## Installation

- Download the jar
- Place it in `<gocd installation directory>/plugins/external`
- Restart the GoCD server.

## Configuration

Navigate to `Admin > Plugins > Bitbucket Notification Plugin` and click the gear icon on the right.

### Required settings
- **Go Server URL** - Address of the GoCD server, e.g. http://localhost:8153
- **Bitbucket Server URL** - Address of the Bitbucket server, e.g. http://localhost:7990 
- **Bitbucket User** - Bitbucket user with access to the rest API
- **Bitbucket Password** - Password for the Bitbucket user

## Building the code base

To build the jar, run `./gradlew clean test assemble`
