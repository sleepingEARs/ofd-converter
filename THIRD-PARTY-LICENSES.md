# Third-Party Licenses

This project is licensed under the [GNU Affero General Public License v3.0](LICENSE).

It uses the following third-party open source projects. Their respective licenses apply to those components.

## Backend Dependencies

| Project | License | Usage |
|---------|---------|-------|
| [OFDRW](https://github.com/ofdrw/ofdrw) | Apache-2.0 | OFD file reading, writing, and conversion |
| [Spring Boot](https://spring.io/projects/spring-boot) | Apache-2.0 | Application framework |
| [Apache POI](https://poi.apache.org/) | Apache-2.0 | DOCX file generation |
| [Apache PDFBox](https://pdfbox.apache.org/) | Apache-2.0 | PDF processing |
| [SQLite JDBC](https://github.com/xerial/sqlite-jdbc) | BSD-2-Clause | SQLite database driver |
| [JUnit 5](https://junit.org/junit5/) | EPL-2.0 | Testing framework |

## Frontend Dependencies

| Project | License | Usage |
|---------|---------|-------|
| [React](https://react.dev/) | MIT | UI framework |
| [Ant Design](https://ant.design/) | MIT | UI component library |
| [ofd.js](https://www.npmjs.com/package/ofd.js) | ISC / Similar permissive | OFD client-side preview (note: this project primarily uses server-side preview) |
| [JSZip](https://stuk.github.io/jszip/) | MIT | ZIP file handling |
| [React Router](https://reactrouter.com/) | MIT | Client-side routing |
| [Vite](https://vitejs.dev/) | MIT | Build tool |
| [Vitest](https://vitest.dev/) | MIT | Testing framework |

## Runtime / Infrastructure

| Project | License | Usage |
|---------|---------|-------|
| [Eclipse Temurin JDK/JRE](https://adoptium.net/) | GPL-2.0-with-classpath-exception | Java runtime for backend container |
| [Nginx](https://nginx.org/) | BSD-2-Clause | Static file server and reverse proxy for frontend container |
| [Maven](https://maven.apache.org/) | Apache-2.0 | Java build tool (used in backend Docker build) |

## Notes

- This list covers the direct/top-level dependencies used by the project.
- Transitive dependencies are subject to their own respective licenses.
- The OFDRW library remains under the Apache-2.0 License even when used as part of this AGPL-3.0 licensed project.
