# Third-party notices

AgriDabaw-3D backend (`agridabao-api`), the server behind the AgriDabaw-3D capstone game by
Daniel C. Galam and Jessica Mae G. Suello, Assumption College of Davao.

This file lists the third-party software this server uses, and the published data its code
stores, so the notices those licences ask for travel with the source. The Android game keeps
its own list on the in-game Credits screen, which covers the game engine, the client
libraries, the typeface, the artwork and the soil, climate and map data shown to players.

Every licence below was read from the library itself: the licence text inside the jar, or the
licence named in the jar's POM or OSGi manifest. The list is the server's runtime classpath.


## Libraries

### Apache License 2.0

<https://www.apache.org/licenses/LICENSE-2.0>

- byte-buddy 1.18.10
- classmate 1.7.3
- commons-logging 1.3.6
- flyway-core 12.4.0
- flyway-database-postgresql 12.4.0
- hibernate-core 7.4.1.Final
- hibernate-models 1.1.1
- hibernate-validator 9.1.0.Final
- HikariCP 7.0.2
- jackson-annotations 2.21
- jackson-core 3.1.4
- jackson-databind 3.1.4
- jakarta.inject-api 2.0.1
- jakarta.validation-api 3.1.1
- jboss-logging 3.6.3.Final
- jspecify 1.0.0
- log4j-api 2.25.4
- log4j-to-slf4j 2.25.4
- micrometer-commons 1.17.0
- micrometer-core 1.17.0
- micrometer-jakarta9 1.17.0
- micrometer-observation 1.17.0
- nimbus-jose-jwt 10.9
- snakeyaml 2.6
- spring-aop 7.0.8
- spring-aspects 7.0.8
- spring-beans 7.0.8
- spring-boot 4.1.0
- spring-boot-actuator 4.1.0
- spring-boot-actuator-autoconfigure 4.1.0
- spring-boot-autoconfigure 4.1.0
- spring-boot-data-commons 4.1.0
- spring-boot-data-jpa 4.1.0
- spring-boot-flyway 4.1.0
- spring-boot-health 4.1.0
- spring-boot-hibernate 4.1.0
- spring-boot-http-converter 4.1.0
- spring-boot-jackson 4.1.0
- spring-boot-jdbc 4.1.0
- spring-boot-jpa 4.1.0
- spring-boot-mail 4.1.0
- spring-boot-micrometer-metrics 4.1.0
- spring-boot-micrometer-observation 4.1.0
- spring-boot-persistence 4.1.0
- spring-boot-security 4.1.0
- spring-boot-security-oauth2-resource-server 4.1.0
- spring-boot-servlet 4.1.0
- spring-boot-sql 4.1.0
- spring-boot-starter 4.1.0
- spring-boot-starter-actuator 4.1.0
- spring-boot-starter-data-jpa 4.1.0
- spring-boot-starter-flyway 4.1.0
- spring-boot-starter-jackson 4.1.0
- spring-boot-starter-jdbc 4.1.0
- spring-boot-starter-logging 4.1.0
- spring-boot-starter-mail 4.1.0
- spring-boot-starter-micrometer-metrics 4.1.0
- spring-boot-starter-oauth2-resource-server 4.1.0
- spring-boot-starter-security 4.1.0
- spring-boot-starter-tomcat 4.1.0
- spring-boot-starter-tomcat-runtime 4.1.0
- spring-boot-starter-validation 4.1.0
- spring-boot-starter-web 4.1.0
- spring-boot-tomcat 4.1.0
- spring-boot-transaction 4.1.0
- spring-boot-validation 4.1.0
- spring-boot-web-server 4.1.0
- spring-boot-webmvc 4.1.0
- spring-context 7.0.8
- spring-context-support 7.0.8
- spring-core 7.0.8
- spring-data-commons 4.1.0
- spring-data-jpa 4.1.0
- spring-expression 7.0.8
- spring-jdbc 7.0.8
- spring-orm 7.0.8
- spring-security-config 7.1.0
- spring-security-core 7.1.0
- spring-security-crypto 7.1.0
- spring-security-oauth2-core 7.1.0
- spring-security-oauth2-jose 7.1.0
- spring-security-oauth2-resource-server 7.1.0
- spring-security-web 7.1.0
- spring-tx 7.0.8
- spring-web 7.0.8
- spring-webmvc 7.0.8
- tomcat-embed-core 11.0.22
- tomcat-embed-el 11.0.22
- tomcat-embed-websocket 11.0.22

### MIT License

<https://opensource.org/license/mit>

The MIT License asks that its copyright notice and permission notice travel with the software. Both jars carry that text at META-INF/LICENSE.txt, and the notice reads "Copyright (c) 2004-2022 QOS.ch Sarl (Switzerland)".

- jul-to-slf4j 2.0.18
- slf4j-api 2.0.18

### BSD 2-Clause License

<https://opensource.org/license/bsd-2-clause>

- HdrHistogram 2.2.2
- postgresql 42.7.11

### BSD 3-Clause License

<https://opensource.org/license/bsd-3-clause>

The Jakarta and JAXB jars here ship the plain BSD 3-Clause text as their licence file at META-INF/LICENSE.md; their projects publish the same text as the Eclipse Distribution License 1.0. ANTLR's licence is published at https://www.antlr.org/license.html.

- angus-activation 2.0.3
- antlr4-runtime 4.13.2
- jakarta.activation-api 2.1.4
- jakarta.xml.bind-api 4.0.5
- jaxb-core 4.0.9
- jaxb-runtime 4.0.9
- txw2 4.0.9

### Eclipse Public License 2.0

<https://www.eclipse.org/legal/epl-2.0/>

- angus-mail 2.0.5

### Eclipse Public License 2.0 or Eclipse Distribution License 1.0

<https://www.eclipse.org/legal/epl-2.0/>

- jakarta.persistence-api 3.2.0

### Eclipse Public License 2.0 or GNU General Public License 2.0 with the Classpath Exception

<https://www.eclipse.org/legal/epl-2.0/>

Offered under either licence. The Classpath Exception means using these APIs as libraries places no licence condition on the code that calls them, and this server uses them unmodified.

- jakarta.annotation-api 3.0.0
- jakarta.mail-api 2.1.5
- jakarta.transaction-api 2.0.1

### Eclipse Public License or GNU Lesser General Public License 2.1

<https://logback.qos.ch/license.html>

Offered under either licence, and used unmodified.

- logback-classic 1.5.34
- logback-core 1.5.34

### Several licences together

- aspectjweaver 1.9.25.1 - EPL-2.0 AND BSD-3-Clause AND Apache-1.1, as stated in LICENSE-AspectJ.adoc inside the jar

Libraries listed: 108.

## Build tooling

The Gradle wrapper (`gradle/wrapper/gradle-wrapper.jar`) is part of Gradle, under the Apache
License 2.0.

## Published data stored by this server

The values below are typed into this repository's source. They come from public documents,
which the project paper cites in full.

- **Crop, seed and planting-material prices** (`farm/EconomyJsonService.java`)
  - Department of Agriculture. (2013). *Administrative Order No. 15, s. 2013: Prescribing the
    selling prices of vegetable seeds and plant materials.*
  - Department of Agriculture. (2024). *Administrative Circular No. 03, s. 2024: Prescribed
    selling price of open-pollinated varieties vegetable seeds and other crops.*
  - Department of Agriculture - Regional Field Office X. (2024). *Procurement of 1,018 bags
    hybrid corn seeds (white), Lot 2* [Bid document].
  - Philippine Statistics Authority Regional Statistical Services Office XI. (2025).
    *Commercial crops 2025 Q1 average farmgate price* [Infographic].
  - Philippine Statistics Authority. (2025). *Seasonally adjusted corn production and prices,
    April to June 2025.*
  - Department of Science and Technology - Philippine Council for Agriculture, Aquatic and
    Natural Resources Research and Development. (n.d.). *Fresh strawberry.* Agricultural
    Technology Business Incubator Marketplace.
  - 93.9 IFM News Davao. (2026). *Presyo sa prutas sa Bankerohan Public Market* [Market price
    reports, 4, 14 and 16 July 2026].

- **District crop pools** (`farm/DistrictSeedPools.java`)
  - City Government of Davao. (2021). *Comprehensive Land Use Plan 2019-2028, Volume 3:
    Sectoral studies.* City Planning and Development Office.
  - City Government of Davao. (2023). *Comprehensive Development Plan 2023-2028.* City
    Planning and Development Office.

## Services this server calls

- **Google Gemini API** (`gemini-2.5-flash`) for the AI Adviser, task generation and the
  climate resilience evaluation, under Google's API terms.
- **Resend** for transactional email.
- **Railway** for application hosting and the PostgreSQL database.

## This project's own code

The rest of this repository is the work of the proponents named above. No third-party source
files are copied into it.
