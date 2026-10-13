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
  movie: Movie;
  reason: string;
}
