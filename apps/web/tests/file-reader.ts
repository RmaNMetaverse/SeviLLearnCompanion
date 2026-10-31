// Minimal browser FileReader equivalent for Node's Blob-based adapter tests.
export class FileReader {
  result: string | null = null;
  error: Error | null = null;
  onload?: () => void;
  onerror?: () => void;
  readAsDataURL(blob: Blob) {
    blob
      .arrayBuffer()
      .then((data) => {
        this.result = `data:${blob.type};base64,${Buffer.from(data).toString("base64")}`;
        this.onload?.();
      })
      .catch((e) => {
        this.error = e;
        this.onerror?.();
      });
  }
}
