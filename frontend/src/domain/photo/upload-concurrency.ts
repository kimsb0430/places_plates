export interface UploadPipelineResult {
  transferResults: boolean[];
  processingResults: boolean[];
  allSucceeded: boolean;
}

/**
 * ファイル転送を制限付きで終えた後、重い画像処理を一件ずつ実行する。
 */
export async function runUploadPipeline<T>(
  items: T[],
  transferWorker: (item: T) => Promise<boolean>,
  processingWorker: (item: T) => Promise<boolean>,
): Promise<UploadPipelineResult> {
  const transferResults = await runWithConcurrency(items, 2, transferWorker);
  const transferredItems = items.filter((_, index) => transferResults[index]);
  const processingResults = await runWithConcurrency(
    transferredItems,
    1,
    processingWorker,
  );
  return {
    transferResults,
    processingResults,
    allSucceeded: transferResults.every(Boolean) && processingResults.every(Boolean),
  };
}

export async function runWithConcurrency<T, R>(
  items: T[],
  concurrency: number,
  worker: (item: T) => Promise<R>,
): Promise<R[]> {
  let nextIndex = 0;
  const results = new Array<R>(items.length);
  const runners = Array.from({ length: Math.min(concurrency, items.length) }, async () => {
    while (nextIndex < items.length) {
      const index = nextIndex;
      const item = items[index];
      nextIndex += 1;
      results[index] = await worker(item);
    }
  });
  await Promise.all(runners);
  return results;
}
