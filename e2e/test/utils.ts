export async function eventually<T>(fn: () => Promise<T>, retries = 150, delay = 100): Promise<T> {
  for (let i = 0; i < retries; i++) {
    try {
      return await fn();
    } catch (e) {
      if (i === retries - 1) throw e;
      await new Promise((res) => setTimeout(res, delay));
    }
  }
  throw new Error("Failed after retries");
}
