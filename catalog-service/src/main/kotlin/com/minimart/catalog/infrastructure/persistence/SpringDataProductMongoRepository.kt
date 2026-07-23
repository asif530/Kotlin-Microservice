package com.minimart.catalog.infrastructure.persistence

import org.springframework.data.mongodb.repository.MongoRepository

interface SpringDataProductMongoRepository : MongoRepository<ProductDocument, String> {

    fun findByIdAndStatus(id: String, status: String): ProductDocument?

    fun findByStatus(status: String): List<ProductDocument>

    fun findByStatusAndCategory(status: String, category: String): List<ProductDocument>
}
