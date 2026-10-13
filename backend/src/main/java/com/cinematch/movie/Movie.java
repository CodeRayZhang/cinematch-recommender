package com.cinematch.movie;

public record Movie(
    long id,
    String title,
    String originalTitle,
    int releaseYear,
    String genres,
    int runtime,
    double rating,
    int ratingCount,
    String director,
    String country,
    String synopsis,
    String posterUrl,
    String backdropUrl,
    boolean featured) {}
