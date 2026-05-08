# Fraud Detection Workflow

## 1. Data Preparation (Offline/Batch)
The system operates on a pre-built index of historical transactions.
- **Vectorization**: Transactions are converted into 14-dimensional feature vectors.
- **Quantization**: Vectors are quantized to 16-bit `short` values to optimize storage and memory footprint.
- **Index Generation**: A Vantage Point Tree (VP-Tree) is constructed to organize the data, though the current search strategy uses a flattened version of this data.
- **Storage**: The index is saved in a custom binary `.vpt` format with 8-byte alignment for efficient memory mapping.

## 2. System Initialization (Startup)
When the service starts, `SearchService` initializes the detection engine:
- **Index Loading**: Loads the `.vpt` file into a `VpTree` object.
- **Strategy Transition**: Converts the tree-structured data into a **Planar (Column-Oriented)** format (`LinearScanEngine`).
- **Memory Layout**: Data is transposed into a `float[14][N]` array, where `N` is the number of transactions. This layout is specifically designed for high-throughput SIMD processing.
- **Warm-up**: The `MccRiskProvider` loads risk scores and normalization constants from JSON resources.

## 3. Transaction Vectorization (Request Phase)
For every incoming `TransactionRequest`, the `TransactionVectorizer` produces a normalized 14D vector:
- **Features**: Includes transaction amount, installments, time features (hour/day), location data (distance from home/last tx), and merchant risk (MCC).
- **Normalization**: Values are clamped and scaled between `[0.0, 1.0]` based on historical bounds defined in `normalization.json`.

## 4. Similarity Search (SIMD Acceleration)
The core detection logic uses a **Vertical SIMD Linear Scan**:
- **Vectorization**: The query vector is broadcast into SIMD registers (`FloatVector`).
- **Parallel Processing**: Processes chunks of transactions (e.g., 8 at a time on AVX-2) using the `jdk.incubator.vector` API.
- **Distance Calculation**: Computes Squared Euclidean Distance using Fused Multiply-Add (`fma`) instructions.
- **Pruning**: Uses horizontal reductions (`reduceLanes`) to skip detailed distance checks for chunks that cannot beat the current worst neighbor in the `KnnQueue`.
- **Top-K**: Collects the 5 nearest neighbors based on feature similarity.

## 5. Scoring & Decision
- **Neighborhood Analysis**: The labels (Fraud/Legitimate) of the 5 nearest neighbors are retrieved.
- **Fraud Score Calculation**:
  $$\text{fraud\_score} = \frac{\text{count of fraudulent neighbors}}{5}$$
- **Thresholding**:
  - `fraud_score >= 0.6` $\rightarrow$ **Reject** (3+ fraudulent neighbors)
  - `fraud_score < 0.6` $\rightarrow$ **Approve**

## Technical Constraints
- **CPU**: Optimized for 1-core environments using SIMD to saturate instruction throughput.
- **Memory**: Designed to fit within 350MB by using efficient primitive arrays and avoiding object overhead.
- **Latency**: Sub-200ms target for full end-to-end scoring.
