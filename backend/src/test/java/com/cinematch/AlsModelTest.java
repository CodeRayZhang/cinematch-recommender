package com.cinematch;

import static org.junit.jupiter.api.Assertions.*;

import com.cinematch.recommender.AlsModel;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class AlsModelTest {
  @Test
  void learnsOppositeTastesAndUpdatesFromLatestRatings() {
    List<AlsModel.Rating> rows = new ArrayList<>();
    for (long u = 1; u <= 20; u++)
      for (long i = 1; i <= 8; i++)
        rows.add(new AlsModel.Rating(u, i, (u <= 10) == (i <= 4) ? 5 : 1, 0));
    var params = new AlsModel.Parameters(2, 20, .01, 42);
    var artifact = AlsModel.train(rows, params, n -> {});
    var model = new AlsModel(artifact);
    var likesFirst = model.predictions(Map.of(1L, 5.0, 2L, 5.0, 5L, 1.0));
    var likesLast = model.predictions(Map.of(1L, 1.0, 2L, 1.0, 5L, 5.0));
    assertTrue(likesFirst.get(3L) > likesFirst.get(6L) + 2);
    assertTrue(likesLast.get(6L) > likesLast.get(3L) + 2);
    var json = JsonMapper.builder().build();
    var restored =
        new AlsModel(json.readValue(json.writeValueAsString(artifact), AlsModel.Artifact.class));
    assertEquals(likesFirst, restored.predictions(Map.of(1L, 5.0, 2L, 5.0, 5L, 1.0)));
    assertEquals(
        json.writeValueAsString(artifact),
        json.writeValueAsString(AlsModel.train(rows, params, n -> {})));
    assertFalse(restored.predict(restored.foldIn(Map.of(1L, 5.0)), 999L).isPresent());
  }

  @Test
  void rejectsCorruptParametersRatingsAndArtifacts() {
    assertThrows(IllegalArgumentException.class, () -> new AlsModel.Parameters(0, 15, .1, 42));
    assertThrows(
        IllegalArgumentException.class, () -> new AlsModel.Parameters(16, 15, Double.NaN, 42));
    var row = new AlsModel.Rating(1, 1, 5, 0);
    assertThrows(
        IllegalArgumentException.class,
        () -> AlsModel.train(List.of(row, row), AlsModel.Parameters.defaults(), n -> {}));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AlsModel.Artifact(
                "cinematch.als.v1",
                new AlsModel.Parameters(1, 1, .1, 42),
                new long[] {1},
                new double[][] {{Double.NaN}}));
  }

  @Test
  void foldInSolvesRegularizedLeastSquaresExactly() {
    var p = new AlsModel.Parameters(2, 1, .1, 42);
    var m =
        new AlsModel(
            new AlsModel.Artifact(
                "cinematch.als.v1",
                p,
                new long[] {1, 2, 3},
                new double[][] {{1, 0}, {0, 1}, {1, 1}}));
    var v = m.foldIn(Map.of(1L, 4.0, 2L, 2.0, 999L, 5.0));
    assertArrayEquals(new double[] {4 / 1.2, 2 / 1.2}, v, 1e-10);
    assertEquals(5, m.predict(v, 3).orElseThrow(), 1e-10);
    assertEquals(2, m.supported(Map.of(1L, 4.0, 2L, 2.0, 999L, 5.0)));
  }
}
