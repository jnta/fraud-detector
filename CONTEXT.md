# CONTEXT: Rinha de Backend 2026 - Fraud Detector

## Overview
A high-performance fraud detection API built for the Rinha de Backend 2026 competition. The system uses vector similarity search (KNN) to classify transactions.

## Architecture
- **Topology:** 1 Nginx Load Balancer -> 2 Micronaut API Replicas.
- **Port:** 9999.
- **Hardware Limits:** 1.0 CPU and 350MB RAM (TOTAL for all containers).

## Domain Logic
- **Vectorization:** 14-dimensional normalized vectors (0.0 to 1.0).
- **Search:** Find top 5 nearest neighbors (K=5).
- **Score:** `fraud_score = (count of fraudulent neighbors) / 5`.
- **Decision:** `approved = score < 0.6`.

## Technical Constraints
- **Memory:** ~163MB dedicated to vector storage per instance (14 dims * 4 bytes * 3M vectors).
- **CPU:** Must utilize SIMD (`jdk.incubator.vector`) to achieve <1ms p99 latency.
- **Storage:** Loads from `.vpt` (VP-Tree) format, transposes to flat linear arrays at runtime.

## Glossary
- **Vectorization:** Process of converting transaction metadata into a 14D float vector.
- **Linear Scan:** Current high-speed search strategy using Vertical SIMD.
- **VPT:** Legacy tree format used for persistent storage.
