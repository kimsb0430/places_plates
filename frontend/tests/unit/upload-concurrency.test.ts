import assert from 'node:assert/strict';
import test from 'node:test';
import { runUploadPipeline } from '../../src/domain/photo/upload-concurrency';

test('limits transfers to two and processes sanitized images sequentially', async () => {
  let activeTransfers = 0;
  let maximumTransfers = 0;
  let activeProcessing = 0;
  let maximumProcessing = 0;
  let completedTransfers = 0;
  const processingStartCounts: number[] = [];

  const result = await runUploadPipeline(
    [1, 2, 3, 4, 5],
    async () => {
      activeTransfers += 1;
      maximumTransfers = Math.max(maximumTransfers, activeTransfers);
      await new Promise((resolve) => setTimeout(resolve, 5));
      activeTransfers -= 1;
      completedTransfers += 1;
      return true;
    },
    async () => {
      processingStartCounts.push(completedTransfers);
      activeProcessing += 1;
      maximumProcessing = Math.max(maximumProcessing, activeProcessing);
      await new Promise((resolve) => setTimeout(resolve, 2));
      activeProcessing -= 1;
      return true;
    },
  );

  assert.equal(maximumTransfers, 2);
  assert.equal(maximumProcessing, 1);
  assert.deepEqual(processingStartCounts, [5, 5, 5, 5, 5]);
  assert.equal(result.allSucceeded, true);
});

test('does not process files whose transfer failed', async () => {
  const processed: number[] = [];
  const result = await runUploadPipeline(
    [1, 2, 3],
    async (item) => item !== 2,
    async (item) => {
      processed.push(item);
      return true;
    },
  );

  assert.deepEqual(processed, [1, 3]);
  assert.deepEqual(result.transferResults, [true, false, true]);
  assert.equal(result.allSucceeded, false);
});
