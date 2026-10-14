import type { Movie } from "./types";

export function ratingLabel(movie: Movie): string {
  if (movie.dataSource === "douban") return "豆瓣参考评分";
  return movie.dataSource === "demo" ? "策展参考评分" : "参考评分";
}

export function sourceNote(movie: Movie): string {
  if (movie.dataSource === "douban")
    return `${movie.sourceSnapshot || "历史"} 年公开数据集快照，非实时评分`;
  return movie.dataSource === "demo"
    ? "演示资料，非实时观众评分"
    : "由管理员维护";
}
