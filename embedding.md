# 余弦距离 SQL 计算表达式

> 基于 pgvector 插件，适用于本项目 `document_chunk` 表的 `embedding vector(1024)` 列。

---

## 1. pgvector 三种距离操作符

| 操作符 | 含义 | 数学公式 | 值范围 | 越小越 |
|--------|------|----------|--------|--------|
| `<=>` | **余弦距离** | 1 - cos(θ) | [0, 2] | 越相似 |
| `<->` | **欧氏距离（L2）** | √Σ(Aᵢ - Bᵢ)² | [0, +∞) | 越相似 |
| `<#>` | **负内积** | -Σ(Aᵢ × Bᵢ) | [-∞, +∞] | 越相似 |

---

## 2. 余弦距离核心表达式

### 2.1 直接计算余弦距离

```sql
-- 余弦距离 = 1 - cos(θ)，范围 [0, 2]，值越小越相似
SELECT embedding <=> '[0.1, 0.2, ...]'::vector AS cosine_distance
FROM document_chunk;
```

### 2.2 由余弦距离推导余弦相似度

```sql
-- 相似度 = 1 - 余弦距离 = cos(θ)，范围 [-1, 1]，值越大越相似
SELECT 1 - (embedding <=> '[0.1, 0.2, ...]'::vector) AS similarity
FROM document_chunk;
```

### 2.3 同时返回余弦距离和相似度

```sql
SELECT
    embedding <=> ?::vector AS cosine_distance,
    1 - (embedding <=> ?::vector) AS similarity
FROM document_chunk;
```

---

## 3. 等价计算（仅限 L2 归一化向量）

当向量已做 L2 归一化（‖A‖ = ‖B‖ = 1）时，三种操作符可互相等价：

### 3.1 用欧氏距离等价计算余弦距离

```sql
-- cosine_distance = L2距离² / 2
SELECT (embedding <-> '[0.1, 0.2, ...]'::vector) ^ 2 / 2 AS cosine_distance
FROM document_chunk;
```

### 3.2 用内积等价计算余弦距离

```sql
-- cosine_distance = 1 + (负内积) = 1 - 内积
SELECT 1 + (embedding <#> '[0.1, 0.2, ...]'::vector) AS cosine_distance
FROM document_chunk;
```

### 3.3 用内积直接计算相似度

```sql
-- similarity = cos(θ) = 内积 = -(负内积)
SELECT -(embedding <#> '[0.1, 0.2, ...]'::vector) AS similarity
FROM document_chunk;
```

---

## 4. 排序与检索表达式

### 4.1 按余弦距离升序排序（最相似优先）

```sql
SELECT id, file_name, chunk_index, chunk_text
FROM document_chunk
ORDER BY embedding <=> '[0.1, 0.2, ...]'::vector
LIMIT 5;
```

### 4.2 按相似度降序排序（最相似优先）

```sql
SELECT id, file_name, chunk_index, chunk_text,
       1 - (embedding <=> '[0.1, 0.2, ...]'::vector) AS similarity
FROM document_chunk
ORDER BY similarity DESC
LIMIT 5;
```

### 4.3 相似度阈值过滤

```sql
-- 只返回相似度 > 0.7 的结果（即余弦距离 < 0.3）
SELECT id, file_name, chunk_text,
       embedding <=> '[0.1, 0.2, ...]'::vector AS cosine_distance,
       1 - (embedding <=> '[0.1, 0.2, ...]'::vector) AS similarity
FROM document_chunk
WHERE embedding <=> '[0.1, 0.2, ...]'::vector < 0.3
ORDER BY embedding <=> '[0.1, 0.2, ...]'::vector;
```

### 4.4 Top-K 查询（本项目实际使用）

```sql
SELECT id, file_name, chunk_index, chunk_text,
       embedding <=> ?::vector AS cosine_distance,
       1 - (embedding <=> ?::vector) AS similarity,
       created_at
FROM document_chunk
ORDER BY embedding <=> ?::vector
LIMIT ?;
```

---

## 5. 聚合统计表达式

### 5.1 统计相似度分布

```sql
SELECT
    AVG(1 - (embedding <=> ?::vector))  AS avg_similarity,
    MAX(1 - (embedding <=> ?::vector))  AS max_similarity,
    MIN(1 - (embedding <=> ?::vector))  AS min_similarity,
    AVG(embedding <=> ?::vector)        AS avg_cosine_distance,
    MAX(embedding <=> ?::vector)        AS max_cosine_distance,
    MIN(embedding <=> ?::vector)        AS min_cosine_distance
FROM document_chunk;
```

### 5.2 按文件分组统计平均相似度

```sql
SELECT
    file_name,
    COUNT(*) AS chunk_count,
    AVG(1 - (embedding <=> ?::vector)) AS avg_similarity
FROM document_chunk
GROUP BY file_name
ORDER BY avg_similarity DESC;
```

---

## 6. 索引相关

### 6.1 IVFFlat 索引（余弦距离）

```sql
-- 使用 vector_cosine_ops 操作符类创建 IVFFlat 索引
CREATE INDEX idx_embedding_cosine
    ON document_chunk USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
```

### 6.2 HNSW 索引（余弦距离）

```sql
-- 使用 vector_cosine_ops 操作符类创建 HNSW 索引
CREATE INDEX idx_embedding_cosine_hnsw
    ON document_chunk USING hnsw (embedding vector_cosine_ops);
```

### 6.3 不同操作符对应的索引类型

| 操作符 | IVFFlat 操作符类 | HNSW 操作符类 |
|--------|------------------|---------------|
| `<=>` | `vector_cosine_ops` | `vector_cosine_ops` |
| `<->` | `vector_l2_ops` | `vector_l2_ops` |
| `<#>` | `vector_ip_ops` | `vector_ip_ops` |

---

## 7. 数学公式与 SQL 对照表

| 数学公式 | SQL 表达式 | 说明 |
|----------|-----------|------|
| cos(θ) = (A·B) / (‖A‖·‖B‖) | `1 - (a <=> b)` | 余弦相似度 |
| D_cos = 1 - cos(θ) | `a <=> b` | 余弦距离 |
| ‖A - B‖₂ | `a <-> b` | 欧氏距离 |
| -A·B | `a <#> b` | 负内积 |
| cos(θ) = A·B（归一化后） | `-(a <#> b)` | 归一化后的相似度 |
| D_cos = ‖A-B‖²/2（归一化后） | `(a <-> b)^2 / 2` | 归一化后的余弦距离 |
