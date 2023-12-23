#!/usr/bin/env bash

spring init \
--boot-version=3.2.1 \
--type=gradle-project \
--java-version=17 \
--packaging=jar \
--name=product-service \
--package-name=re.elio.microservices.core.product \
--groupId=re.elio.microservices.core.product \
--dependencies=actuator,webflux \
--version=1.0.0-SNAPSHOT \
product-service

spring init \
--boot-version=3.2.1 \
--type=gradle-project \
--java-version=17 \
--packaging=jar \
--name=review-service \
--package-name=re.elio.microservices.core.review \
--groupId=re.elio.microservices.core.review \
--dependencies=actuator,webflux \
--version=1.0.0-SNAPSHOT \
review-service

spring init \
--boot-version=3.2.1 \
--type=gradle-project \
--java-version=17 \
--packaging=jar \
--name=recommendation-service \
--package-name=re.elio.microservices.core.recommendation \
--groupId=re.elio.microservices.core.recommendation \
--dependencies=actuator,webflux \
--version=1.0.0-SNAPSHOT \
recommendation-service

spring init \
--boot-version=3.2.1 \
--type=gradle-project \
--java-version=17 \
--packaging=jar \
--name=product-composite-service \
--package-name=re.elio.microservices.composite.product \
--groupId=re.elio.microservices.composite.product \
--dependencies=actuator,webflux \
--version=1.0.0-SNAPSHOT \
product-composite-service