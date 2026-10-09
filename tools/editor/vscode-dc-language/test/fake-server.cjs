let buffer = Buffer.alloc(0);
function emit(value) {
  const data = Buffer.from(JSON.stringify(value));
  const frame = Buffer.concat([Buffer.from(`Content-Length: ${data.length}\r\n\r\n`), data]);
  process.stdout.write(frame.subarray(0, 13));
  process.stdout.write(frame.subarray(13));
}
process.stdin.on("data", (chunk) => {
  buffer = Buffer.concat([buffer, chunk]);
  for (;;) {
    const end = buffer.indexOf("\r\n\r\n");
    if (end < 0) break;
    const size = Number(/Content-Length:\s*(\d+)/i.exec(buffer.subarray(0, end).toString())[1]);
    if (buffer.length < end + 4 + size) break;
    const value = JSON.parse(buffer.subarray(end + 4, end + 4 + size));
    buffer = buffer.subarray(end + 4 + size);
    if (value.method === "initialize") emit({ jsonrpc: "2.0", id: value.id, result: { capabilities: { textDocumentSync: 2 } } });
    else if (value.method === "echo") emit({ jsonrpc: "2.0", id: value.id, result: value.params });
    else if (value.method === "textDocument/didOpen") emit({ jsonrpc: "2.0", method: "textDocument/publishDiagnostics", params: { uri: value.params.textDocument.uri, version: value.params.textDocument.version, diagnostics: [] } });
    else if (value.method === "shutdown") emit({ jsonrpc: "2.0", id: value.id, result: null });
    else if (value.method === "exit") process.exit(0);
    else if (value.method === "crash") process.exit(12);
  }
});
