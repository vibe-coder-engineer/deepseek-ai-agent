package ru.sibgatulinanton.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.AttributeFactory;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class RagOperationService {

    private static final String ID_FIELD = "id";
    private static final String TITLE_FIELD = "title";
    private static final String TEXT_FIELD = "text";
    private static final String CREATED_AT_FIELD = "createdAt";
    private static final String VECTOR_FIELD = "vector";
    private static final int DEFAULT_MAX_MATCHES = 5;
    private static final int DEFAULT_LIST_LIMIT = 50;
    private static final int EMBEDDING_DIMENSIONS = 384;
    private static final int MAX_SNIPPET_LENGTH = 700;
    private static final int RELATED_LIMIT = 4;

    private static final String SCOPE_GLOBAL = "global";
    private static final Path GLOBAL_SEED_ENTRIES_FOLDER = Paths.get("resources")
            .resolve("rag")
            .resolve("global")
            .resolve("entries");

    private final Path defaultWorkspace;
    private final Path globalEntriesFolder;
    private final ObjectMapper mapper = JsonMapper.builder().build();
    private final Map<Path, LuceneRagIndex> indexesByEntriesFolder = new LinkedHashMap<Path, LuceneRagIndex>();

    public RagOperationService(Path workspace) {
        this.defaultWorkspace = workspace.toAbsolutePath().normalize();
        this.globalEntriesFolder = Paths.get(System.getProperty("user.home"))
                .resolve(".deepseek-ai-agent")
                .resolve("rag")
                .resolve("global")
                .resolve("entries")
                .toAbsolutePath()
                .normalize();
    }

    public String add(String data, String content) {
        try {
            JsonNode options = readOptionalObject(content);
            Path entriesFolder = entriesFolderFor(options);
            ensureStorage(entriesFolder);
            RagEntry entry = buildEntry(data, content);
            Path target = entriesFolder.resolve(entry.id + ".json");

            ObjectNode json = mapper.createObjectNode();
            json.put("id", entry.id);
            json.put("title", entry.title);
            json.put("text", entry.text);
            json.put("createdAt", entry.createdAt);
            ArrayNode tags = json.putArray("tags");
            for (String tag : entry.tags) {
                tags.add(tag);
            }

            Files.write(target,
                    mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(json),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
            reloadIndex(entriesFolder);
            return "RAG_OPERATION_OK\nADD_RAG\nSCOPE: " + scopeName(entriesFolder)
                    + "\nID: " + entry.id + "\nPATH: " + target;
        } catch (Exception e) {
            return error("ADD_RAG", data, e);
        }
    }

    public String addGlobal(String data, String content) {
        return add(data, forceScope(content, SCOPE_GLOBAL));
    }

    public String remove(String data, String optionsJson) {
        try {
            JsonNode options = readOptionalJson(optionsJson);
            Path entriesFolder = entriesFolderFor(options);
            ensureStorage(entriesFolder);
            String id = safeId(data);
            if (id.isEmpty()) {
                return "RAG_OPERATION_ERROR\nREMOVE_RAG\nID: " + data + "\nERROR: id is empty";
            }

            Path target = entriesFolder.resolve(id + ".json");
            boolean deleted = Files.deleteIfExists(target);
            if (!deleted) {
                return "RAG_OPERATION_ERROR\nREMOVE_RAG\nID: " + id + "\nERROR: entry not found";
            }
            reloadIndex(entriesFolder);
            return "RAG_OPERATION_OK\nREMOVE_RAG\nSCOPE: " + scopeName(entriesFolder) + "\nID: " + id;
        } catch (Exception e) {
            return error("REMOVE_RAG", data, e);
        }
    }

    public String removeGlobal(String data) {
        return remove(data, "{\"scope\":\"global\"}");
    }

    public String search(String query, String optionsJson) {
        try {
            JsonNode options = readOptionalJson(optionsJson);
            int maxMatches = positiveInt(options, "maxMatches", DEFAULT_MAX_MATCHES);
            Path entriesFolder = entriesFolderFor(options);
            LuceneRagIndex currentIndex = index(entriesFolder);
            List<SearchMatch> matches = currentIndex.search(query, maxMatches);

            StringBuilder result = new StringBuilder();
            result.append("RAG_SEARCH_RESULT\n")
                    .append("SCOPE: ").append(scopeName(entriesFolder)).append('\n')
                    .append("STORAGE: ").append(entriesFolder).append('\n')
                    .append("INDEX: lucene in-memory hnsw vector index\n")
                    .append("EMBEDDING: local deterministic hash embedding\n")
                    .append("DOCUMENTS: ").append(currentIndex.size()).append('\n')
                    .append("QUERY: ").append(query == null ? "" : query).append('\n')
                    .append("MATCHES: ").append(matches.size()).append('\n');
            if (matches.isEmpty()) {
                result.append("<no matches>\n");
                return result.toString();
            }

            for (int i = 0; i < matches.size(); i++) {
                SearchMatch match = matches.get(i);
                result.append("-- match ").append(i + 1).append(" --\n")
                        .append("ID: ").append(match.entry.id).append('\n')
                        .append("TITLE: ").append(match.entry.title).append('\n')
                        .append("SCORE: ").append(formatScore(match.score)).append('\n')
                        .append("RELATED: ").append(joinRelated(currentIndex.relatedIds(match.entry.id))).append('\n')
                        .append("TEXT:\n").append(snippet(match.entry.text)).append('\n');
            }
            return result.toString();
        } catch (Exception e) {
            return error("SEARCH_RAG", query, e);
        }
    }

    public String searchGlobal(String query, String optionsJson) {
        return search(query, forceScope(optionsJson, SCOPE_GLOBAL));
    }

    public String list(String optionsJson) {
        try {
            JsonNode options = readOptionalJson(optionsJson);
            int maxMatches = positiveInt(options, "maxMatches", DEFAULT_LIST_LIMIT);
            Path entriesFolder = entriesFolderFor(options);
            LuceneRagIndex currentIndex = index(entriesFolder);
            List<RagEntry> entries = currentIndex.entries();
            Collections.sort(entries, new Comparator<RagEntry>() {
                @Override
                public int compare(RagEntry left, RagEntry right) {
                    return left.id.compareTo(right.id);
                }
            });

            StringBuilder result = new StringBuilder();
            result.append("RAG_LIST_RESULT\n")
                    .append("SCOPE: ").append(scopeName(entriesFolder)).append('\n')
                    .append("STORAGE: ").append(entriesFolder).append('\n')
                    .append("INDEX: lucene in-memory hnsw vector index\n")
                    .append("ENTRIES: ").append(entries.size()).append('\n');
            int emitted = Math.min(entries.size(), maxMatches);
            for (int i = 0; i < emitted; i++) {
                RagEntry entry = entries.get(i);
                result.append("- ID: ").append(entry.id)
                        .append("\n  TITLE: ").append(entry.title)
                        .append("\n  CREATED_AT: ").append(entry.createdAt)
                        .append("\n  RELATED: ").append(joinRelated(currentIndex.relatedIds(entry.id)))
                        .append('\n');
            }
            if (entries.size() > emitted) {
                result.append("TRUNCATED: true\n");
            }
            return result.toString();
        } catch (Exception e) {
            return error("LIST_RAG", "", e);
        }
    }

    public String listGlobal(String optionsJson) {
        return list(forceScope(optionsJson, SCOPE_GLOBAL));
    }

    private synchronized LuceneRagIndex index(Path entriesFolder) throws IOException {
        ensureStorage(entriesFolder);
        LuceneRagIndex index = indexesByEntriesFolder.get(entriesFolder);
        if (index == null || index.isStale(storageVersion(entriesFolder))) {
            reloadIndex(entriesFolder);
            index = indexesByEntriesFolder.get(entriesFolder);
        }
        return index;
    }

    private synchronized void reloadIndex(Path entriesFolder) throws IOException {
        ensureStorage(entriesFolder);
        LuceneRagIndex previous = indexesByEntriesFolder.get(entriesFolder);
        indexesByEntriesFolder.put(entriesFolder, LuceneRagIndex.build(readEntries(entriesFolder), storageVersion(entriesFolder)));
        closeQuietly(previous);
    }

    private List<RagEntry> readEntries(Path entriesFolder) throws IOException {
        List<RagEntry> entries = new ArrayList<RagEntry>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(entriesFolder, "*.json")) {
            for (Path path : stream) {
                entries.add(readEntry(path));
            }
        }
        return entries;
    }

    private long storageVersion(Path entriesFolder) throws IOException {
        long version = 0L;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(entriesFolder, "*.json")) {
            for (Path path : stream) {
                version = Math.max(version, Files.getLastModifiedTime(path).toMillis());
            }
        }
        return version;
    }

    private void ensureStorage(Path entriesFolder) throws IOException {
        Files.createDirectories(entriesFolder);
        seedGlobalStorage(entriesFolder);
    }

    private void seedGlobalStorage(Path entriesFolder) throws IOException {
        if (!entriesFolder.equals(globalEntriesFolder)) {
            return;
        }

        Path seedFolder = defaultWorkspace.resolve(GLOBAL_SEED_ENTRIES_FOLDER).toAbsolutePath().normalize();
        if (!Files.isDirectory(seedFolder)) {
            return;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(seedFolder, "*.json")) {
            for (Path seedEntry : stream) {
                Path target = entriesFolder.resolve(seedEntry.getFileName());
                if (!Files.exists(target)) {
                    Files.copy(seedEntry, target, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
    }

    private Path entriesFolderFor(JsonNode options) {
        if (SCOPE_GLOBAL.equalsIgnoreCase(text(options, "scope", ""))) {
            return globalEntriesFolder;
        }

        String workspace = text(options, "workspace", "");
        if (workspace.trim().isEmpty()) {
            workspace = text(options, "projectRoot", "");
        }
        if (workspace.trim().isEmpty()) {
            workspace = text(options, "root", "");
        }

        Path projectRoot = workspace.trim().isEmpty()
                ? defaultWorkspace
                : Paths.get(workspace.trim());
        if (!projectRoot.isAbsolute()) {
            projectRoot = defaultWorkspace.resolve(projectRoot);
        }
        return projectRoot.toAbsolutePath().normalize().resolve(".rag").resolve("entries");
    }

    private String scopeName(Path entriesFolder) {
        return entriesFolder.equals(globalEntriesFolder) ? "global" : "local";
    }

    private String forceScope(String json, String scope) {
        try {
            ObjectNode options;
            JsonNode parsed = tryReadJson(json);
            if (parsed != null && parsed.isObject()) {
                options = (ObjectNode) parsed;
            } else {
                options = mapper.createObjectNode();
                if (json != null && !json.trim().isEmpty()) {
                    options.put("text", json);
                }
            }
            options.put("scope", scope);
            return options.toString();
        } catch (IOException e) {
            ObjectNode options = mapper.createObjectNode();
            options.put("scope", scope);
            options.put("text", json == null ? "" : json);
            return options.toString();
        }
    }

    private RagEntry buildEntry(String data, String content) throws IOException {
        String title = data == null ? "" : data.trim();
        String text = content == null ? "" : content.trim();
        List<String> tags = new ArrayList<String>();

        JsonNode json = tryReadJson(content);
        if (json != null && json.isObject()) {
            title = text(json, "title", title);
            text = text(json, "text", text(json, "content", ""));
            JsonNode tagsNode = json.get("tags");
            if (tagsNode != null && tagsNode.isArray()) {
                for (JsonNode tag : tagsNode) {
                    if (tag != null && !tag.isNull()) {
                        tags.add(tag.asText(""));
                    }
                }
            }
        }

        if (text.trim().isEmpty()) {
            throw new IllegalArgumentException("content is empty");
        }

        String id = safeId(title);
        if (id.isEmpty()) {
            id = "entry-" + System.currentTimeMillis();
        }

        String createdAt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.ROOT).format(new Date());
        return new RagEntry(id, title.isEmpty() ? id : title, text, tags, createdAt);
    }

    private RagEntry readEntry(Path path) throws IOException {
        JsonNode json = mapper.readTree(Files.readAllBytes(path));
        List<String> tags = new ArrayList<String>();
        JsonNode tagsNode = json.get("tags");
        if (tagsNode != null && tagsNode.isArray()) {
            for (JsonNode tag : tagsNode) {
                tags.add(tag.asText(""));
            }
        }
        return new RagEntry(
                text(json, "id", stripJsonExtension(path.getFileName().toString())),
                text(json, "title", ""),
                text(json, "text", ""),
                tags,
                text(json, "createdAt", "")
        );
    }

    private JsonNode readOptionalJson(String json) throws IOException {
        if (json == null || json.trim().isEmpty()) {
            return mapper.createObjectNode();
        }
        return mapper.readTree(json);
    }

    private JsonNode readOptionalObject(String json) throws IOException {
        JsonNode parsed = tryReadJson(json);
        return parsed != null && parsed.isObject() ? parsed : mapper.createObjectNode();
    }

    private JsonNode tryReadJson(String json) throws IOException {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            return mapper.readTree(json);
        } catch (IOException ignored) {
            return null;
        }
    }

    private int positiveInt(JsonNode node, String field, int defaultValue) {
        int value = node.has(field) ? node.get(field).asInt(defaultValue) : defaultValue;
        return Math.max(1, value);
    }

    private String text(JsonNode node, String field, String defaultValue) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asText() : defaultValue;
    }

    private String safeId(String value) {
        String raw = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        String safe = raw.replaceAll("[^\\p{L}\\p{Nd}._-]+", "-")
                .replaceAll("-+", "-")
                .replaceAll("(^[-.]+|[-.]+$)", "");
        if (safe.length() > 80) {
            safe = safe.substring(0, 80);
        }
        return safe;
    }

    private String snippet(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.trim();
        if (normalized.length() <= MAX_SNIPPET_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_SNIPPET_LENGTH) + "\n...";
    }

    private String stripJsonExtension(String fileName) {
        return fileName.endsWith(".json") ? fileName.substring(0, fileName.length() - 5) : fileName;
    }

    private String joinRelated(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return "<none>";
        }
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                result.append(", ");
            }
            result.append(ids.get(i));
        }
        return result.toString();
    }

    private String formatScore(double score) {
        return String.format(Locale.ROOT, "%.4f", score);
    }

    private void closeQuietly(Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (IOException ignored) {
        }
    }

    private String error(String operation, String data, Exception e) {
        String error = e.getMessage() == null ? e.toString() : e.getMessage();
        return "RAG_OPERATION_ERROR\n" + operation + "\nDATA: " + data + "\nERROR: " + error;
    }

    private static class LuceneRagIndex implements Closeable {
        private final Directory directory;
        private final DirectoryReader reader;
        private final IndexSearcher searcher;
        private final Map<String, RagEntry> entriesById;
        private final Map<String, List<String>> relatedById;
        private final long version;

        private LuceneRagIndex(Directory directory,
                               DirectoryReader reader,
                               Map<String, RagEntry> entriesById,
                               Map<String, List<String>> relatedById,
                               long version) {
            this.directory = directory;
            this.reader = reader;
            this.searcher = new IndexSearcher(reader);
            this.entriesById = entriesById;
            this.relatedById = relatedById;
            this.version = version;
        }

        static LuceneRagIndex build(List<RagEntry> entries, long version) throws IOException {
            Directory directory = new ByteBuffersDirectory();
            IndexWriterConfig config = new IndexWriterConfig(new NoOpAnalyzer());
            Map<String, RagEntry> entriesById = new LinkedHashMap<String, RagEntry>();
            try (IndexWriter writer = new IndexWriter(directory, config)) {
                for (RagEntry entry : entries) {
                    writer.addDocument(toDocument(entry));
                    entriesById.put(entry.id, entry);
                }
                writer.commit();
            }

            DirectoryReader reader = DirectoryReader.open(directory);
            LuceneRagIndex index = new LuceneRagIndex(directory,
                    reader,
                    entriesById,
                    new LinkedHashMap<String, List<String>>(),
                    version);
            index.relatedById.putAll(index.buildRelatedGraph());
            return index;
        }

        boolean isStale(long currentVersion) {
            return version != currentVersion;
        }

        int size() {
            return entriesById.size();
        }

        List<RagEntry> entries() {
            return new ArrayList<RagEntry>(entriesById.values());
        }

        List<SearchMatch> search(String query, int maxMatches) throws IOException {
            if (entriesById.isEmpty() || query == null || query.trim().isEmpty()) {
                return Collections.emptyList();
            }
            Query vectorQuery = KnnFloatVectorField.newVectorQuery(VECTOR_FIELD, HashEmbedding.embed(query), maxMatches);
            TopDocs topDocs = searcher.search(vectorQuery, maxMatches);
            List<SearchMatch> matches = new ArrayList<SearchMatch>();
            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                Document doc = searcher.doc(scoreDoc.doc);
                RagEntry entry = entriesById.get(doc.get(ID_FIELD));
                if (entry != null) {
                    matches.add(new SearchMatch(entry, scoreDoc.score));
                }
            }
            return matches;
        }

        List<String> relatedIds(String id) {
            List<String> related = relatedById.get(id);
            if (related == null) {
                return Collections.emptyList();
            }
            return related;
        }

        private Map<String, List<String>> buildRelatedGraph() throws IOException {
            Map<String, List<String>> result = new LinkedHashMap<String, List<String>>();
            for (RagEntry entry : entriesById.values()) {
                Query vectorQuery = KnnFloatVectorField.newVectorQuery(VECTOR_FIELD,
                        HashEmbedding.embed(entry.searchText()),
                        RELATED_LIMIT + 1);
                TopDocs topDocs = searcher.search(vectorQuery, RELATED_LIMIT + 1);
                List<String> related = new ArrayList<String>();
                for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                    Document doc = searcher.doc(scoreDoc.doc);
                    String relatedId = doc.get(ID_FIELD);
                    if (!entry.id.equals(relatedId)) {
                        related.add(relatedId);
                    }
                    if (related.size() >= RELATED_LIMIT) {
                        break;
                    }
                }
                result.put(entry.id, related);
            }
            return result;
        }

        private static Document toDocument(RagEntry entry) {
            Document document = new Document();
            document.add(new StringField(ID_FIELD, entry.id, Field.Store.YES));
            document.add(new StoredField(TITLE_FIELD, entry.title));
            document.add(new StoredField(TEXT_FIELD, entry.text));
            document.add(new StoredField(CREATED_AT_FIELD, entry.createdAt));
            document.add(new KnnFloatVectorField(VECTOR_FIELD, HashEmbedding.embed(entry.searchText())));
            return document;
        }

        @Override
        public void close() throws IOException {
            reader.close();
            directory.close();
        }
    }

    private static class HashEmbedding {
        static float[] embed(String text) {
            float[] vector = new float[EMBEDDING_DIMENSIONS];
            String[] tokens = tokenize(text);
            for (String token : tokens) {
                if (token.length() < 2) {
                    continue;
                }
                int hash = token.hashCode();
                int index = Math.floorMod(hash, EMBEDDING_DIMENSIONS);
                vector[index] += (hash & 1) == 0 ? 1.0f : -1.0f;
            }
            normalize(vector);
            return vector;
        }

        private static String[] tokenize(String value) {
            String source = value == null ? "" : value.toLowerCase(Locale.ROOT);
            return source.split("[^\\p{L}\\p{Nd}_]+");
        }

        private static void normalize(float[] vector) {
            double norm = 0.0d;
            for (float value : vector) {
                norm += value * value;
            }
            if (norm == 0.0d) {
                vector[0] = 1.0f;
                return;
            }
            float sqrtNorm = (float) Math.sqrt(norm);
            for (int i = 0; i < vector.length; i++) {
                vector[i] = vector[i] / sqrtNorm;
            }
        }
    }

    private static class NoOpAnalyzer extends Analyzer {
        @Override
        protected TokenStreamComponents createComponents(String fieldName) {
            return new TokenStreamComponents(new EmptyTokenizer());
        }
    }

    private static class EmptyTokenizer extends Tokenizer {
        EmptyTokenizer() {
            super(AttributeFactory.DEFAULT_ATTRIBUTE_FACTORY);
        }

        @Override
        public boolean incrementToken() {
            return false;
        }

    }

    private static class RagEntry {
        private final String id;
        private final String title;
        private final String text;
        private final List<String> tags;
        private final String createdAt;

        RagEntry(String id, String title, String text, List<String> tags, String createdAt) {
            this.id = id;
            this.title = title;
            this.text = text;
            this.tags = tags;
            this.createdAt = createdAt;
        }

        String searchText() {
            StringBuilder result = new StringBuilder();
            result.append(title).append('\n').append(text);
            for (String tag : tags) {
                result.append('\n').append(tag);
            }
            return result.toString();
        }
    }

    private static class SearchMatch {
        private final RagEntry entry;
        private final float score;

        SearchMatch(RagEntry entry, float score) {
            this.entry = entry;
            this.score = score;
        }
    }
}
