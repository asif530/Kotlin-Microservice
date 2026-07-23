package com.minimart.catalog.infrastructure.persistence

import com.minimart.catalog.domain.model.Product
import com.minimart.catalog.domain.model.ProductStatus
import com.minimart.catalog.domain.port.ProductRepositoryPort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Repository
import java.util.UUID

/**
 * Adapter implementing the domain's outbound port on top of Spring Data
 * MongoDB. This is the only place in the codebase allowed to know that
 * products are stored in Mongo's `products` collection.
 */
@Repository
class ProductRepositoryAdapter(
    private val mongoRepository: SpringDataProductMongoRepository,
    private val mongoTemplate: MongoTemplate,
) : ProductRepositoryPort {

    override fun insert(product: Product): Product = mongoRepository.insert(product.toDocument()).toDomain()

    override fun findVisibleById(id: UUID): Product? =
        mongoRepository.findByIdAndStatus(id.toString(), ProductStatus.ACTIVE.name)?.toDomain()

    override fun searchVisible(category: String?): List<Product> {
        val documents = if (category.isNullOrBlank()) {
            mongoRepository.findByStatus(ProductStatus.ACTIVE.name)
        } else {
            mongoRepository.findByStatusAndCategory(ProductStatus.ACTIVE.name, category)
        }
        return documents.map { it.toDomain() }
    }

    override fun findById(id: UUID): Product? = mongoRepository.findById(id.toString()).map { it.toDomain() }.orElse(null)

    override fun update(product: Product): Product = mongoRepository.save(product.toDocument()).toDomain()

    override fun deleteById(id: UUID) = mongoRepository.deleteById(id.toString())

    override fun reserveStock(id: UUID, quantity: Int): Boolean {
        val query = Query(
            Criteria.where("_id").`is`(id.toString())
                .and("status").`is`(ProductStatus.ACTIVE.name)
                .and("stockCount").gte(quantity),
        )
        val update = Update().inc("stockCount", -quantity)
        return mongoTemplate.updateFirst(query, update, ProductDocument::class.java).modifiedCount == 1L
    }

    override fun releaseStock(id: UUID, quantity: Int): Boolean {
        val query = Query(Criteria.where("_id").`is`(id.toString()).and("status").`is`(ProductStatus.ACTIVE.name))
        val update = Update().inc("stockCount", quantity)
        return mongoTemplate.updateFirst(query, update, ProductDocument::class.java).modifiedCount == 1L
    }
}
