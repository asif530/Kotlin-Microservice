package com.minimart.catalog.infrastructure.persistence

import jakarta.annotation.PostConstruct
import org.bson.Document
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.CollectionOptions
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.springframework.data.mongodb.core.index.TextIndexDefinition
import org.springframework.data.mongodb.core.validation.Validator
import org.springframework.stereotype.Component

/**
 * Idempotently creates the `products` collection — with its `$jsonSchema`
 * validator and indexes, verbatim from Archive/Development/Database §2.1 /
 * Archive/Development/Database-Dev/mongo/00_catalog_schema.js — on startup.
 *
 * catalog-service's build.gradle.kts already carries a `flamingock-core`
 * dependency for eventual Mongo schema migrations (per
 * Archive/Architecture/ARCHITECTURE.md §9), but that same file's own comment
 * flags Flamingock's Gradle plugin/annotation-processor wiring as unverified
 * in this session ("do not copy this comment's absence of a plugin block as
 * evidence migrations will work without further setup"). Building Phase-3's
 * real endpoints on top of an unverified migration tool would risk a
 * runtime failure with no fallback, so this uses a plain `MongoTemplate`
 * call instead — the same idempotent-on-every-startup shape a migration
 * would have, self-contained within catalog-service like identity-service's
 * own Flyway migrations, without depending on Flamingock's DSL actually
 * working. Revisit once Flamingock's setup is confirmed against its current
 * docs.
 */
@Component
class CatalogSchemaInitializer(
    private val mongoTemplate: MongoTemplate,
) {

    private val logger = LoggerFactory.getLogger(CatalogSchemaInitializer::class.java)

    @PostConstruct
    fun ensureProductsCollection() {
        if (!mongoTemplate.collectionExists(COLLECTION_NAME)) {
            val options = CollectionOptions.empty()
                .validator(Validator.document(productJsonSchema()))
                .strictValidation()
                .failOnValidationError()
            mongoTemplate.createCollection(COLLECTION_NAME, options)
            logger.info("Created '{}' collection with its \$jsonSchema validator", COLLECTION_NAME)
        }

        val indexOps = mongoTemplate.indexOps(COLLECTION_NAME)
        // createIndex (not the deprecated ensureIndex) — idempotent at the MongoDB level:
        // creating an index with the same key spec that already exists is a no-op.
        indexOps.createIndex(Index().on("status", Sort.Direction.ASC).on("category", Sort.Direction.ASC))
        indexOps.createIndex(
            TextIndexDefinition.builder()
                .onField("name", 5.0f)
                .onField("description", 1.0f)
                .build(),
        )
    }

    private fun productJsonSchema(): Document = Document(
        "\$jsonSchema",
        Document()
            .append("bsonType", "object")
            .append(
                "required",
                listOf("_id", "name", "description", "category", "unitPrice", "stockCount", "status", "createdAt", "updatedAt"),
            )
            .append(
                "properties",
                Document()
                    .append("_id", Document("bsonType", "string"))
                    .append("name", Document("bsonType", "string").append("minLength", 1))
                    .append("description", Document("bsonType", "string"))
                    .append("category", Document("bsonType", "string").append("minLength", 1))
                    .append(
                        "unitPrice",
                        Document("bsonType", "decimal").append("minimum", 0).append("exclusiveMinimum", true),
                    )
                    .append("stockCount", Document("bsonType", "int").append("minimum", 0))
                    .append("status", Document("enum", listOf("ACTIVE", "DEACTIVATED")))
                    .append("createdAt", Document("bsonType", "date"))
                    .append("updatedAt", Document("bsonType", "date")),
            ),
    )

    private companion object {
        const val COLLECTION_NAME = "products"
    }
}
