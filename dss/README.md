# SQL Hammer

## Overview

SQL Hammer is a web app that helps individuals and mid-sized teams work with SQL  data in operational settings. Of course we all know we're not "supposed" to access SQL directly this way, but everyone does. You're not alone.

Key features:

* Execute ad-hoc SQL commands against any JDBC connection

* Save queries with dynamic run-time parameter lists

* Share / bookmark queries for non-technical users (including parameters)

* Export query results to CSV

* Authentication via OAuth2 or simple email/password lists

* Granular auditing in support of regulatory requirements

SQL Hammer is open-sourced under an MIT license. It is simple by design, but I encourage you to add or request features that would make it more effective for your unique circumstances; it's always fun to improve a useful tool.

There is a ton of background and detail in [my original SQL Hammer blog post](https://shutdownhook.com/2023/12/18/sql-hammer-everything-is-a-nail/), but start here because this content is updated as the app changes. You can also [try a live demo version of SQL Hammer](https://shutdownapps.duckdns.org:7083/), which includes a scratch database you can experiment with.

# Installation

Java v11+ is the only requirement to run SQL Hammer. Most testing is done on Linux, but it runs fine on Windows and MacOS as well (please let me know if you find otherwise!):

1. Download the latest release bundle from GitHub.

2. Unzip it on your local machine.

3. Run `setup-init.sh` (or `.bat`) to configure the application (more on this below).

4. Run `run.sh` (or `.bat`) to start the server. By default logs will be written to `dss0.log` in your home directory.

5. Open a browser to `http[s]://localhost:[port]/`. 

# Initial Configuration

The setup-init.sh script prompts for key configuration values. For most of these you can simply accept defaults to get started:

1. The location of the metadata file that holds information about connections, queries and users. By default this is placed in your home directory.

2. The listening port for the server. By default this is 7071; be sure that the port is open for requests if you have a firewall installed.

3. SSL configuration. If you choose to run with SSL (recommended of course), you will need certificate and key files in the same format used by Apache mod_ssl. If you accept the default values, a self-signed certificate will be used, and you'll have to proceed past browser trust warnings.

4. Authentication type. 
   
   1. The simplest option here is (ha) "Simple", which stores username/password combinations (safely hashed) directly in the configuration file. This is the easiest way to get started. Using a corporate email address as the username will make it easier to convert to an OAuth2/SSO option later.
   
   2. "OAuth2" delegates authentication to an external identity provider. By default the system supports Google, Facebook, GitHub and Amazon providers, each of which will show a documentation URL for setting up your client ID and secret. Most enterprise SSO systems also support this method, but you'll have to consult their documentation for more details.
   
   3. "Force" is an option really intended only for developers working on the application. But you can enable it to avoid all authentication if desired; a hard-coded token will be used for all requests.

That's enough to get you going. The first user to log into the app will be granted full access to manage the metadata store; details on this are in the section "Connection Management."

# Ongoing Configuration

Use `setup.sh` (or `.bat`) at any time to update your configuration. Type `help` or `?` to see a list of options. Be sure to `save` any configuration changes and restart the server to pick them up.

If you're using simple authentication, use `simple_upsert`, `simple_delete` and `simple_list` to manage user accounts. 

There are quite a few values in the JSON file that aren't supported by the setup script. Most will never be needed, but can be manually updated if desired. For example, you can customize logging output by using `LoggingConfigPath` to specify your own `logging.properties` file. [The code is always your best reference](https://github.com/seanno/shutdownhook/blob/dss.6/dss/server/src/main/java/com/shutdownhook/dss/server/Server.java#L29) to all of these.

# The User Interface

When you first log into SQL Hammer, it will look something like this:

![SQL Hammer UX](https://raw.githubusercontent.com/seanno/shutdownhook/main/dss/img/ux1.png)

As the first user of the system, you have been granted access to the DSS Metadata Store, which holds all information about connections, queries and users. Management of these is performed using SQL commands rather than custom user interface; a number of helpful queries have been pre-configured to help you with these tasks.

Each "connection" provides access to a specific data source. "Queries" are associated with a connection and are always owned by a single user who may edit or delete them. the owner may mark a query as "shared", meaning it can be executed by any user with access to the connection. 

The "Schema" button will display information about tables in the selected connection, and "New" will open an editor to start a new query. Selecting a query and choosing "run" will result in a screen something like this one:

![SQL Hammer Run Query UX](https://raw.githubusercontent.com/seanno/shutdownhook/main/dss/img/ux2.png)

Results are displayed in the lower panel and can be exported to CSV or as a shareable (but still access-controlled) URL. 

For "owned" queries, the "Edit" button opens up a syntax-highlighting editor:

![](https://raw.githubusercontent.com/seanno/shutdownhook/main/dss/img/ux3.png)

Queries may also contain dynamic parameters that are provided at runtime. Especially combined with the "shared" query option, this is a great way to provide controlled access to data for less technical or trusted users.

The "Parameters" list for a query must be a comma-separated list of parameter names. to provide a default value for a parameter, append a colon (:) and the default value to the name. The query itself must contain an equivalent number of ? markers to designate where parameters will be inserted. There isn't a ton of error checking here, so mind your commas, colons and question marks!

# Managing Connections

Connections are held in the `connections` table of the Metadata Store. There are shared queries for adding connections, or you can just use SQL commands to manage the table yourself.

![Connections Table Schema](https://raw.githubusercontent.com/seanno/shutdownhook/main/dss/img/table_connections.png)

Most important is the `connection_string` column, which must contain a complete [JDBC connection string](https://www.baeldung.com/java-jdbc-url-format) including any necessary authentication information. 

The SQL Hammer package includes drivers for [SQLite](https://sqlite.org/index.html), [mySQL](https://www.mysql.com/) and [postreSQL](https://www.postgresql.org/) databases. Any database with a JDBC driver will work, but you may have to add the driver JAR to the classpath argument in your `run.sh` script. For example, this edit inclues the driver for Azure SQL databases (`mssql-jdbc-12.4.2.jre11.jar`):

```bash
nohup java \
  -cp dss-server-1.0-SNAPSHOT.jar:mssql-jdbc-12.4.2.jre11.jar \
  com.shutdownhook.dss.server.App \
  config.json &
```

# Managing Access

Access rules are held in the `access` table of the Metadata Store. As with connections, you can edit this table by hand or by using predefined shared queries.

![Access Table Schema](https://raw.githubusercontent.com/seanno/shutdownhook/main/dss/img/table_access.png)

The `user` column should match email addresses or usernames as returned from the authentication model you've chosen. You can also use SQL wildcard matching in this column; for example the value `%@mycompany.com` will match any email address in the mycompany.com domain. `%` by itself will match any user. 

The `can_create` column indicates whether matching users are able to create and share their own queries in the specified connection. If the column is false (0 or null), users may only run queries marked as shared.

Note that a logged-in user may match multiple rows in this table. For example, there may be a `%` rule to allow all users to run shared queries, but a specific `somebody@company.com` one that includes the `can_create` flag. This is fine; the user will be granted the highest level of access found across matching rows.

# The Queries Table

Typically you won't need to edit the `queries` table directly --- but there's nothing stopping you. For example, you might want to update the `owner` field on a query if a colleague changes responsibilities or leaves the company. Don't be shy!

# Auditing

If the `log_queries` column in the connections table is set to true (1), all queries executed in that connection will be logged according to the settings in logging.properties (by default, files in `~/dss#.log`). Audit entries are tagged with the string `[AUDIT]` and include the action, connection, query text and parameters.

# Building from source

See [SQL Hammer (everything is a nail) @ Shutdown Hook](https://shutdownhook.com/2023/12/18/sql-hammer-everything-is-a-nail/#:~:text=Building%20from%20source).

# Support

I created SQL Hammer becaues I've needed something similar at pretty much every job I've ever had, and finally decided to do something more permanent about it. Whether you use the packaged version or build and extend it from source, I hope you find it useful. 

There is no formal support for SQL Hammer, but I'm always happy to help if I can. [Contact me via my site Shutdown Hook](https://shutdownhook.com/contact/), or [submit an issue on GitHub]([Issues · seanno/shutdownhook · GitHub](https://github.com/seanno/shutdownhook/issues). Thanks!
