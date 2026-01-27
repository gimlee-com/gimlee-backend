package com.gimlee.ads.domain

import com.gimlee.ads.domain.model.Category
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.StringField
import org.apache.lucene.document.TextField
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.index.Term
import org.apache.lucene.search.*
import org.apache.lucene.store.ByteBuffersDirectory
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicReference

@Component
class CategorySuggester {
    private val log = LoggerFactory.getLogger(javaClass)
    private val directory = ByteBuffersDirectory()
    private val analyzer = StandardAnalyzer()
    private val searcherReference = AtomicReference<IndexSearcher>()

    companion object {
        private const val FIELD_ID = "id"
        private const val FIELD_NAME_PREFIX = "name_"
    }

    fun reindex(categories: List<Category>) {
        log.info("Reindexing {} categories in Lucene", categories.size)
        val writerConfig = IndexWriterConfig(analyzer)
        IndexWriter(directory, writerConfig).use { writer ->
            writer.deleteAll()
            categories.forEach { category ->
                val doc = Document()
                doc.add(StringField(FIELD_ID, category.id.toString(), Field.Store.YES))
                
                category.name.forEach { (lang, catName) ->
                    doc.add(TextField("$FIELD_NAME_PREFIX$lang", catName.name, Field.Store.NO))
                }
                writer.addDocument(doc)
            }
            writer.commit()
        }
        val reader = DirectoryReader.open(directory)
        searcherReference.set(IndexSearcher(reader))
    }

    fun search(queryStr: String, language: String, limit: Int = 10): List<SearchResult> {
        val searcher = searcherReference.get() ?: return emptyList()
        val field = "$FIELD_NAME_PREFIX$language"
        
        try {
            val bqb = BooleanQuery.Builder()
            val term = Term(field, queryStr.lowercase())
            
            // Exact/Prefix match gets higher priority
            bqb.add(BoostQuery(PrefixQuery(term), 2.0f), BooleanClause.Occur.SHOULD)
            // Fuzzy match for typos
            bqb.add(FuzzyQuery(term, 1), BooleanClause.Occur.SHOULD)
            
            val topDocs = searcher.search(bqb.build(), limit)
            return topDocs.scoreDocs.map {
                SearchResult(searcher.doc(it.doc).get(FIELD_ID).toInt(), it.score)
            }
        } catch (e: Exception) {
            log.error("Error searching categories in Lucene", e)
            return emptyList()
        }
    }

    data class SearchResult(val id: Int, val score: Float)
}
