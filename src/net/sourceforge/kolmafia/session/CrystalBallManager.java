package net.sourceforge.kolmafia.session;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.sourceforge.kolmafia.KoLAdventure;
import net.sourceforge.kolmafia.KoLCharacter;
import net.sourceforge.kolmafia.MonsterData;
import net.sourceforge.kolmafia.combat.MonsterStatusTracker;
import net.sourceforge.kolmafia.persistence.AdventureQueueDatabase;
import net.sourceforge.kolmafia.preferences.Preferences;

public final class CrystalBallManager {
  private static final Pattern[] CRYSTAL_BALL_PATTERNS = {
    Pattern.compile("your next fight will be against <b>an? (.*?)</b>"),
    Pattern.compile("next monster in this (?:zone is going to|area will) be <b>an? (.*?)</b>"),
    Pattern.compile("Look out, there's <b>an? (.*?)</b> right around the next corner"),
    Pattern.compile("There's a little you fighting a little <b>(.*?)</b>"),
    Pattern.compile("How do you feel about fighting <b>an? (.*?)</b>\\? Coz that's"),
    Pattern.compile("the next monster in this area will be <b>an? (.*?)</b>"),
    Pattern.compile("and see a tiny you fighting a tiny <b>(.*?)</b> in a tiny"),
    Pattern.compile("it looks like there's <b>an? (.*?)</b> prowling around"),
    Pattern.compile("and see yourself running into <b>an? (.*?)</b> soon"),
    Pattern.compile("showing you an image of yourself fighting <b>an? (.*?)</b>"),
    Pattern.compile("if you stick around here you're going to run into <b>an? (.*?)</b>")
  };

  public static class Prediction implements Comparable<Prediction> {
    public final int turnCount;
    public final String location;
    public final String monster;

    private Prediction(final int turnCount, final String location, final String monster) {
      this.turnCount = turnCount;
      this.location = location;
      this.monster = monster;
    }

    @Override
    public int compareTo(final Prediction o) {
      if (this.turnCount != o.turnCount) {
        return Integer.valueOf(this.turnCount).compareTo(o.turnCount);
      }

      return this.location.compareTo(o.location);
    }

    @Override
    public String toString() {
      return this.turnCount + ":" + this.location + ":" + this.monster;
    }
  }

  public static final Map<String, Prediction> predictions = new HashMap<>();

  public static void reset() {
    CrystalBallManager.predictions.clear();

    String[] predictions = Preferences.getString("crystalBallPredictions").split("\\|");

    for (final String prediction : predictions) {
      String[] parts = prediction.split(":", 3);

      if (parts.length < 3) {
        continue;
      }

      try {
        CrystalBallManager.predictions.put(
            parts[1], new Prediction(Integer.parseInt(parts[0]), parts[1], parts[2]));
      } catch (NumberFormatException e) {
        continue;
      }
    }
  }

  private static void updatePreference() {
    List<String> predictions =
        CrystalBallManager.predictions.values().stream()
            .sorted()
            .map(p -> p.toString())
            .collect(Collectors.toList());

    Preferences.setString("crystalBallPredictions", String.join("|", predictions));
  }

  /** Parses an in-combat miniature crystal ball prediction. */
  public static void parseCrystalBall(final String responseText) {
    String predictedMonster = parseCrystalBallMonster(responseText);

    if (predictedMonster == null) {
      return;
    }

    String lastAdventureName = KoLAdventure.lastLocationName;

    CrystalBallManager.predictions.put(
        lastAdventureName,
        new Prediction(KoLCharacter.getCurrentRun(), lastAdventureName, predictedMonster));

    updatePreference();

    AdventureQueueDatabase.enqueue(KoLAdventure.lastVisitedLocation(), predictedMonster);
  }

  private static String parseCrystalBallMonster(final String responseText) {
    for (Pattern p : CRYSTAL_BALL_PATTERNS) {
      Matcher matcher = p.matcher(responseText);
      if (matcher.find()) {
        return matcher.group(1);
      }
    }

    return null;
  }

  public static void updateCrystalBallPredictions() {
    if (KoLAdventure.lastVisitedLocation() == null) {
      return;
    }

    String lastAdventureName = KoLAdventure.lastLocationName;

    final Iterator<Prediction> it = CrystalBallManager.predictions.values().iterator();
    while (it.hasNext()) {
      Prediction prediction = it.next();

      if (!prediction.location.equals(lastAdventureName)
          && prediction.turnCount + 2 <= KoLCharacter.getCurrentRun()) {
        it.remove();
      }
    }

    updatePreference();
  }

  // EncounterManager methods

  public static boolean isCrystalBallZone(final String zone) {
    for (final Prediction prediction : CrystalBallManager.predictions.values()) {
      if (prediction.location.equalsIgnoreCase(zone)) {
        return true;
      }
    }

    return false;
  }

  public static boolean isCrystalBallMonster() {
    return CrystalBallManager.isCrystalBallMonster(
        MonsterStatusTracker.getLastMonsterName(), Preferences.getString("nextAdventure"));
  }

  public static boolean isCrystalBallMonster(final MonsterData monster, final String zone) {
    return CrystalBallManager.isCrystalBallMonster(monster.getName(), zone);
  }

  public static boolean isCrystalBallMonster(final String monster, final String zone) {
    // There's no message to check for so assume the correct monster in the correct zone is from the
    // crystal ball
    for (final Prediction prediction : CrystalBallManager.predictions.values()) {
      if (prediction.monster.equalsIgnoreCase(monster)
          && prediction.location.equalsIgnoreCase(zone)) {
        return true;
      }
    }

    return false;
  }
}
