package com.minimart.notification.infrastructure.persistence

import jakarta.annotation.PostConstruct
import org.bson.Document
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.CollectionOptions
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.springframework.data.mongodb.core.validation.Validator
import org.springframework.stereotype.Component

/**
 * Idempotently creates the `notifications` collection — with its
 * `$jsonSchema` validator and index, verbatim from
 * Archive/Development/Database §4.1 /
 * Archive/Development/Database-Dev/mongo/00_notifications_schema.js — on
 * startup. Mirrors catalog-service's CatalogSchemaInitializer exactly,
 * including why this uses a plain `MongoTemplate` call instead of the
 * still-unverified Flamingock setup (see that class's kdoc for the full
 * reasoning — the same applies here).
 */
@Component
class NotificationSchemaInitializer(
    private val mongoTemplate: MongoTemplate,
) {

    private val logger = LoggerFactory.getLogger(NotificationSchemaInitializer::class.java)

    @PostConstruct
    fun ensureNotificationsCollection() {
        if (!mongoTemplate.collectionExists(COLLECTION_NAME)) {
            val options = CollectionOptions.empty()
                .validator(Validator.document(notificationJsonSchema()))
                .strictValidation()
                .failOnValidationError()
            mongoTemplate.createCollection(COLLECTION_NAME, options)
            logger.info("Created '{}' collection with its \$jsonSchema validator", COLLECTION_NAME)
        }

        mongoTemplate.indexOps(COLLECTION_NAME)
            .createIndex(Index().on("accountId", Sort.Direction.ASC).on("createdAt", Sort.Direction.DESC))
    }

    private fun notificationJsonSchema(): Document = Document(
        "\$jsonSchema",
        Document()
            .append("bsonType", "object")
            .append("required", listOf("_id", "accountId", "orderId", "type", "createdAt"))
            .append(
                "properties",
                Document()
                    .append("_id", Document("bsonType", "string"))
                    .append("accountId", Document("bsonType", "string"))
                    .append("orderId", Document("bsonType", "string"))
                    .append("type", Document("enum", listOf("ORDER_PLACED", "ORDER_CANCELLED")))
                    .append("message", Document("bsonType", "string"))
                    .append("createdAt", Document("bsonType", "date")),
            ),
    )

    private companion object {
        const val COLLECTION_NAME = "notifications"
    }
}
