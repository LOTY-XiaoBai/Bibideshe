export function postBlocks(value) {
  try {
    const v = JSON.parse(value || "[]");
    return Array.isArray(v) ? v : [];
  } catch {
    return [];
  }
}
export function postImages(post) {
  return postBlocks(post.blocks)
    .filter(
      (b) => b.type === "image" && /^\/uploads\/image\/[\w.-]+$/.test(b.value),
    )
    .map((b) => b.value);
}
export function postText(post) {
  return postBlocks(post.blocks)
    .filter((b) => b.type === "text")
    .map((b) => b.value)
    .join("\n");
}
export function authorId(row) {
  return (
    row.author_id ||
    String(row.owner || "")
      .split(":")
      .slice(1)
      .join(":")
  );
}
export function displayTime(value) {
  return String(value || "")
    .replace("T", " ")
    .slice(0, 16);
}
export function flag(value) {
  return value === true || value === 1 || value === "1";
}
