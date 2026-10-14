export interface Movie {
  id: number;
  title: string;
  originalTitle: string;
  releaseYear: number;
  genres: string;
  runtime: number;
  rating: number;
  ratingCount: number;
  director: string;
  country: string;
  synopsis: string;
  posterUrl: string;
  backdropUrl: string;
  featured: boolean;
  dataSource: "demo" | "manual" | "douban";
  sourceId: string | null;
  sourceUrl: string | null;
  sourceSnapshot: string | null;
}
export interface User {
  id: number;
  username: string;
  displayName: string;
  role: "USER" | "ADMIN";
}
export interface Entry {
  movieId: number;
  status: "WATCHLIST" | "WATCHED";
}
export interface Review {
  movieId: number;
  stars: number;
  body: string;
}
export interface Library {
  entries: Entry[];
  movies: Movie[];
  reviews: Review[];
}
export interface MoviePage {
  items: Movie[];
  total: number;
  page: number;
  size: number;
}
export interface Pick {
  requestId?: string | null;
  movie: Movie;
  reason: string;
  exploration: boolean;
}

export interface Profile {
  displayName: string;
  bio: string;
  preferredGenres: string;
  joinedAt: string;
}

export interface LibraryRow {
  movie: Movie;
  status: Entry["status"] | null;
  stars: number | null;
  body: string | null;
}
export interface LibraryPage {
  items: LibraryRow[];
  total: number;
  page: number;
  size: number;
}
export interface LibrarySummary {
  watching: number;
  watched: number;
  rated: number;
  minutes: number;
  average: number | null;
  genres: { name: string; count: number }[];
}
export interface Collection {
  id: string;
  name: string;
  description: string;
  coverMovieId: number | null;
  coverUrl: string;
  movieCount: number;
  updatedAt: string;
}
export interface CollectionItem {
  movie: Movie;
  note: string;
  position: number;
}
export interface CollectionPage {
  collection: Collection;
  items: CollectionItem[];
  total: number;
  page: number;
  size: number;
}
export interface CatalogTopic {
  id: string;
  title: string;
  description: string;
  genre: string;
  yearFrom: number;
  yearTo: number;
  maxRuntime: number;
  position: number;
  enabled: boolean;
}
export interface RecommendationMetrics {
  days: number;
  algorithmVersion: string;
  attributionDays: number;
  retainedRequestsPerUser: number;
  groups: {
    version: string;
    mode: string;
    segment: string;
    users: number;
    requests: number;
    served: number;
    exposed: number;
    clicked: number;
    saved: number;
    rated: number;
    highlyRated: number;
    clickRate: number | null;
    saveRate: number | null;
    ratingRate: number | null;
  }[];
}
