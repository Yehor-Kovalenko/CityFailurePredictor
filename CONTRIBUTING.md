## The purpose
The document describes general rules and enforced recommendations for anyone deciding to contribute to the project.

## Contribution note about adding new services
When you want to add new microservice please do not forget to:
- Add path filter predicate to the ApiGateway properties + predicate for swagger (`<new-service-name>/**`)
- Add correlationId filter that will pass the correlation id, handle one or generate a new one and save to logs
- Add `log-volume` to the service alongside with `LOG_FILE` env variable in the docker-compose.yml
- Add Dockerfile to the respective newly created service folder, as well as relative section in the root docker-compose file
- Add log configuration file (could be copy-pasted from another service) under resources directory
- Add requirem pom dependencies (discovery, prometheus, logging, config, swagger)
- Add job name in the `prometheus.yml` to collect metrics for the newly added service
- Add GlobalExceptionHandler to monitor exception with prometheus
