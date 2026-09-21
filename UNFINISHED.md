# Unfinished Work

While the core ledger engine, the document store, and the performance benchmark are fully operational, the following aspects remain unfinished:

### 1. Task 0 (App Profiling & Feedback)
- **Status**: Missing
- **Reason**: I did not have a physical device available to download the Simplify Money application, nor did I have the time to aggregate feedback from 3 individuals. 

### 2. Task 1 (Track Teardown)
- **Status**: Missing
- **Reason**: As I was unable to use the application to connect a live data source, I could not physically generate the teardown screenshots or visually diagnose missing transactions on the UI.

### 3. Edge Case: Extreme Non-Transaction Noise
- **Status**: Partially Complete
- **Reason**: The current implementation relies on recognized bank transaction templates. It rejects known non-transaction formats, but it has not been exhaustively validated against adversarial messages that imitate transaction wording.
