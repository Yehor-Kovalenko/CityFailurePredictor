## The purpose
The document describes general rules and enforced recommendations for anyone deciding to contribute to the project.

## Contribution note about adding new services
When you want to add new microservice please do not forget to:
- Add path filter predicate to the ApiGateway properties + predicate for swagger (`<new-service-name>/**`)
- Add correlationId filter that will pass the correlation id, handle one or generate a new one and save to logs