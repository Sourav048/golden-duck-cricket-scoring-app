package com.goldenduck.scoring

import com.google.ai.client.generativeai.type.Schema
import com.google.ai.client.generativeai.type.Tool
import com.google.ai.client.generativeai.type.defineFunction
import org.json.JSONArray
import org.json.JSONObject

/**
 * Defines Google Gemini Tool and FunctionDeclarations for database queries.
 */
object AiToolDeclarations {

    val resolvePlayerByNameTool = defineFunction(
        name = "resolvePlayerByName",
        description = "Resolves a player's exact ID and details using fuzzy and partial name matching. Use this when the user mentions a player's name or nickname.",
        parameters = listOf(
            Schema.str("query", "The player name or partial name to search for")
        ),
        requiredParameters = listOf("query")
    )

    val findPlayerMatchByKnockTool = defineFunction(
        name = "findPlayerMatchByKnock",
        description = "Finds match IDs and details for a player's specific innings or knock (e.g. 61 runs) or lists all innings played by a player. Use this whenever the user asks about a specific knock, commentary, or ball-by-ball performance by a player to obtain the matchId automatically.",
        parameters = listOf(
            Schema.str("playerName", "Name or nickname of the player"),
            Schema.int("targetRuns", "Optional target runs scored in the knock (e.g. 61)")
        ),
        requiredParameters = listOf("playerName")
    )

    val getPlayerStatsTool = defineFunction(
        name = "getPlayerStats",
        description = "Gets complete career or recent stats (runs, balls faced, times out, not-outs, batting average, strike rate, wickets, balls bowled, economy rate) for a specific player ID.",
        parameters = listOf(
            Schema.str("playerId", "The unique player ID"),
            Schema.int("lastNMatches", "Optional number of recent matches to filter form (e.g. 5)")
        ),
        requiredParameters = listOf("playerId")
    )

    val getPlayerStatsByNameTool = defineFunction(
        name = "getPlayerStatsByName",
        description = "Gets complete career or recent stats for a player directly by their name or nickname in ONE step. Use this whenever the user asks about a player's stats by name.",
        parameters = listOf(
            Schema.str("playerName", "The player name or nickname to search for"),
            Schema.int("lastNMatches", "Optional number of recent matches to filter form (e.g. 5)")
        ),
        requiredParameters = listOf("playerName")
    )

    val getLeaderboardTool = defineFunction(
        name = "getLeaderboard",
        description = "Gets the top leaderboard records across all players in the league.",
        parameters = listOf(
            Schema.str("statType", "Type of stat: MOST_RUNS, BEST_STRIKE_RATE, MOST_SIXES, MOST_FOURS, HIGHEST_SCORE, MOST_FIFTIES, MOST_EIGHTIES, MOST_THIRTIES, MOST_DUCKS, MOST_WICKETS, BEST_BOWLING_INNINGS, BEST_BOWLING_AVERAGE, BEST_ECONOMY, MOST_HATTRICKS, MOST_FIVE_WICKETS, MOST_THREE_WICKETS, MOST_TWO_WICKETS, MOST_CATCHES, MOST_STUMPINGS, MOST_RUN_OUTS"),
            Schema.int("limit", "Number of top players to return (default 10)")
        ),
        requiredParameters = listOf("statType")
    )

    val getRankingsAndPrestigeTool = defineFunction(
        name = "getRankingsAndPrestige",
        description = "Gets top 3 prestige badge winners (Gold, Silver, Bronze) and rank points for overall, batting, or bowling categories.",
        parameters = listOf(
            Schema.str("category", "Category: OVERALL, BATTING, or BOWLING")
        ),
        requiredParameters = listOf("category")
    )

    val getMatchListTool = defineFunction(
        name = "getMatchList",
        description = "Searches and lists matches filtered by status, team name, venue, or date (e.g. '3rd October', '03 Oct').",
        parameters = listOf(
            Schema.str("status", "Match status filter: FINISHED, LIVE, or ABANDONED"),
            Schema.str("teamName", "Optional team name to filter"),
            Schema.str("venue", "Optional venue name to filter"),
            Schema.str("dateQuery", "Optional date string or month to search for matches (e.g. '3rd October' or '03 Oct')")
        )
    )

    val getMatchFullDetailsTool = defineFunction(
        name = "getMatchFullDetails",
        description = "Gets detailed match summary, including scores, toss decision, rules, venue, and Player of the Match.",
        parameters = listOf(
            Schema.str("matchId", "The unique match ID")
        ),
        requiredParameters = listOf("matchId")
    )

    val getMatchScorecardTool = defineFunction(
        name = "getMatchScorecard",
        description = "Gets individual player scorecard performance (runs, balls, 4s, 6s, wickets, overs, fielding) for a specific match ID.",
        parameters = listOf(
            Schema.str("matchId", "The unique match ID")
        ),
        requiredParameters = listOf("matchId")
    )

    val getMatchBreakdownTool = defineFunction(
        name = "getMatchBreakdown",
        description = "Gets ball-by-ball delivery list, commentary text, fall of wickets, or partnerships for a match innings.",
        parameters = listOf(
            Schema.str("matchId", "The unique match ID"),
            Schema.int("inningsNumber", "Innings number: 1 or 2"),
            Schema.str("queryType", "Type of breakdown: BALL_BY_BALL, COMMENTARY, FALL_OF_WICKETS, or PARTNERSHIPS")
        ),
        requiredParameters = listOf("matchId")
    )

    val getBatterVsBowlerMatchupTool = defineFunction(
        name = "getBatterVsBowlerMatchup",
        description = "Gets micro head-to-head performance of a batter against a specific bowler, or a complete breakdown against ALL bowlers if bowlerName is omitted or blank (runs, 6s, 4s, SR, dismissals).",
        parameters = listOf(
            Schema.str("batterName", "Name of the batter"),
            Schema.str("bowlerName", "Optional name of the bowler. Leave blank or omit to get breakdown against all bowlers."),
            Schema.int("inningsNumber", "Optional innings filter: 1 or 2"),
            Schema.str("matchId", "Optional match ID to restrict query to a specific match")
        ),
        requiredParameters = listOf("batterName")
    )

    val getLiveMatchStatusTool = defineFunction(
        name = "getLiveMatchStatus",
        description = "Gets current live match state, active striker, non-striker, active bowler, required runs, target, and free hit status.",
        parameters = listOf(
            Schema.str("matchId", "Optional specific live match ID")
        )
    )

    val getTeamRecordsAndStandingsTool = defineFunction(
        name = "getTeamRecordsAndStandings",
        description = "Gets team win/loss records, win percentages, standings, and highest team scores.",
        parameters = listOf(
            Schema.str("teamName", "Optional specific team name")
        )
    )

    val getVenueAnalyticsTool = defineFunction(
        name = "getVenueAnalytics",
        description = "Gets venue statistics including average 1st and 2nd innings scores, toss win percentage, and total matches played at the venue.",
        parameters = listOf(
            Schema.str("venueName", "Name of the venue")
        ),
        requiredParameters = listOf("venueName")
    )

    val searchCommentaryTool = defineFunction(
        name = "searchCommentary",
        description = "Searches commentary text, event logs, dropped catches, user commentary notes, highlights, and moments across all matches in the league by query keywords (e.g. 'chotu ne catch chhod diya', 'dropped catch', 'six', 'run out') or player name.",
        parameters = listOf(
            Schema.str("query", "Keywords or phrase to search in commentary text (e.g. 'catch chhod', 'dropped', 'chotu')"),
            Schema.str("playerName", "Optional player name filter"),
            Schema.str("matchId", "Optional specific match ID filter")
        ),
        requiredParameters = listOf("query")
    )

    val getPlayerDismissalAnalysisTool = defineFunction(
        name = "getPlayerDismissalAnalysis",
        description = "Analyzes how a player gets out (modes of dismissal: Bowled, Caught, LBW, Run Out, Stumped, Hit Wicket), their nemesis bowler (bowler who dismissed them most), who caught them most, and their not-out percentage.",
        parameters = listOf(
            Schema.str("playerName", "Name or nickname of the player to analyze")
        ),
        requiredParameters = listOf("playerName")
    )

    val getPlayerStatsAgainstTeamTool = defineFunction(
        name = "getPlayerStatsAgainstTeam",
        description = "Gets a player's complete batting and bowling statistics specifically against an opposition team (runs, average, strike rate, boundaries, wickets, economy, and win/loss record).",
        parameters = listOf(
            Schema.str("playerName", "Name or nickname of the player"),
            Schema.str("opponentTeam", "Name or partial name of the opposition team")
        ),
        requiredParameters = listOf("playerName", "opponentTeam")
    )

    val getPlayerPhaseOfPlayStatsTool = defineFunction(
        name = "getPlayerPhaseOfPlayStats",
        description = "Gets a player's batting and bowling performance broken down by cricket match phases: Powerplay (early overs), Middle overs, and Death overs (final overs).",
        parameters = listOf(
            Schema.str("playerName", "Name or nickname of the player"),
            Schema.str("phase", "Optional phase filter: ALL, POWERPLAY, MIDDLE, or DEATH")
        ),
        requiredParameters = listOf("playerName")
    )

    val getPlayerWinImpactStatsTool = defineFunction(
        name = "getPlayerWinImpactStats",
        description = "Analyzes a player's match-winning impact and contribution: team win percentage when they play vs overall win percentage, win rate when scoring 25+ runs or taking 2+ wickets, and Player of the Match awards.",
        parameters = listOf(
            Schema.str("playerName", "Name or nickname of the player")
        ),
        requiredParameters = listOf("playerName")
    )

    val getPlayerPersonalBestsTool = defineFunction(
        name = "getPlayerPersonalBests",
        description = "Gets all career personal records and bests for a player: best batting innings (highest score with balls and SR), best bowling spell (e.g. 4/12), most boundaries in an innings, and best fielding performance.",
        parameters = listOf(
            Schema.str("playerName", "Name or nickname of the player")
        ),
        requiredParameters = listOf("playerName")
    )

    val compareTwoPlayersTool = defineFunction(
        name = "compareTwoPlayers",
        description = "Compares two cricket players side-by-side in batting, bowling, strike rates, averages, boundaries, wickets, economy, and their head-to-head matchup.",
        parameters = listOf(
            Schema.str("player1", "Name or nickname of the first player"),
            Schema.str("player2", "Name or nickname of the second player")
        ),
        requiredParameters = listOf("player1", "player2")
    )

    val getPlayerBestPartnershipsTool = defineFunction(
        name = "getPlayerBestPartnerships",
        description = "Finds a player's top batting partnerships across all matches in the league (highest partnership runs, partner name, balls faced, and individual contributions).",
        parameters = listOf(
            Schema.str("playerName", "Name or nickname of the player"),
            Schema.str("partnerName", "Optional specific batting partner name to filter")
        ),
        requiredParameters = listOf("playerName")
    )

    val getPlayerInningsSplitTool = defineFunction(
        name = "getPlayerInningsSplit",
        description = "Analyzes a player's performance when setting a target (1st Innings) vs chasing a target (2nd Innings), including runs, average, strike rate, wickets, economy, and win percentage in chases.",
        parameters = listOf(
            Schema.str("playerName", "Name or nickname of the player")
        ),
        requiredParameters = listOf("playerName")
    )

    val getPlayerFormStreaksTool = defineFunction(
        name = "getPlayerFormStreaks",
        description = "Calculates a player's current and best career streaks (consecutive matches scoring 20+/30+, consecutive games with wickets taken, consecutive boundary matches) and upcoming milestone progress.",
        parameters = listOf(
            Schema.str("playerName", "Name or nickname of the player")
        ),
        requiredParameters = listOf("playerName")
    )

    val aiDatabaseTools = Tool(
        listOf(
            resolvePlayerByNameTool,
            findPlayerMatchByKnockTool,
            getPlayerStatsTool,
            getPlayerStatsByNameTool,
            getLeaderboardTool,
            getRankingsAndPrestigeTool,
            getMatchListTool,
            getMatchFullDetailsTool,
            getMatchScorecardTool,
            getMatchBreakdownTool,
            getBatterVsBowlerMatchupTool,
            getLiveMatchStatusTool,
            getTeamRecordsAndStandingsTool,
            getVenueAnalyticsTool,
            searchCommentaryTool,
            getPlayerDismissalAnalysisTool,
            getPlayerStatsAgainstTeamTool,
            getPlayerPhaseOfPlayStatsTool,
            getPlayerWinImpactStatsTool,
            getPlayerPersonalBestsTool,
            compareTwoPlayersTool,
            getPlayerBestPartnershipsTool,
            getPlayerInningsSplitTool,
            getPlayerFormStreaksTool
        )
    )

    val groqToolsJsonArray: JSONArray by lazy {
        JSONArray().apply {
            put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "resolvePlayerByName")
                    put("description", "Resolves a player's exact ID and details using fuzzy and partial name matching. Use this when the user mentions a player's name or nickname.")
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject().apply {
                            put("query", JSONObject().apply {
                                put("type", "string")
                                put("description", "The player name or partial name to search for")
                            })
                        })
                        put("required", JSONArray().put("query"))
                    })
                })
            })
            put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "findPlayerMatchByKnock")
                    put("description", "Finds match IDs and details for a player's specific innings or knock (e.g. 61 runs) or lists all innings played by a player. Use this whenever the user asks about a specific knock, commentary, or ball-by-ball performance by a player to obtain the matchId automatically.")
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject().apply {
                            put("playerName", JSONObject().apply {
                                put("type", "string")
                                put("description", "Name or nickname of the player")
                            })
                            put("targetRuns", JSONObject().apply {
                                put("type", "integer")
                                put("description", "Optional target runs scored in the knock (e.g. 61)")
                            })
                        })
                        put("required", JSONArray().put("playerName"))
                    })
                })
            })
            put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "getPlayerStats")
                    put("description", "Gets complete career or recent stats (runs, balls faced, times out, not-outs, batting average, strike rate, wickets, balls bowled, economy rate) for a specific player ID.")
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject().apply {
                            put("playerId", JSONObject().apply {
                                put("type", "string")
                                put("description", "The unique player ID")
                            })
                            put("lastNMatches", JSONObject().apply {
                                put("type", "integer")
                                put("description", "Optional number of recent matches to filter form (e.g. 5)")
                            })
                        })
                        put("required", JSONArray().put("playerId"))
                    })
                })
            })
            put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "getPlayerStatsByName")
                    put("description", "Gets complete career or recent stats for a player directly by their name or nickname in ONE step.")
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject().apply {
                            put("playerName", JSONObject().apply {
                                put("type", "string")
                                put("description", "The player name or nickname to search for")
                            })
                            put("lastNMatches", JSONObject().apply {
                                put("type", "integer")
                                put("description", "Optional number of recent matches to filter form (e.g. 5)")
                            })
                        })
                        put("required", JSONArray().put("playerName"))
                    })
                })
            })
            put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "getLeaderboard")
                    put("description", "Gets the top leaderboard records across all players in the league.")
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject().apply {
                            put("statType", JSONObject().apply {
                                put("type", "string")
                                put("description", "Type of stat: MOST_RUNS, BEST_STRIKE_RATE, MOST_SIXES, MOST_FOURS, HIGHEST_SCORE, MOST_FIFTIES, MOST_EIGHTIES, MOST_THIRTIES, MOST_DUCKS, MOST_WICKETS, BEST_BOWLING_INNINGS, BEST_BOWLING_AVERAGE, BEST_ECONOMY, MOST_HATTRICKS, MOST_FIVE_WICKETS, MOST_THREE_WICKETS, MOST_TWO_WICKETS, MOST_CATCHES, MOST_STUMPINGS, MOST_RUN_OUTS")
                            })
                            put("limit", JSONObject().apply {
                                put("type", "integer")
                                put("description", "Number of top players to return (default 10)")
                            })
                        })
                        put("required", JSONArray().put("statType"))
                    })
                })
            })
            put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "getRankingsAndPrestige")
                    put("description", "Gets top 3 prestige badge winners (Gold, Silver, Bronze) and rank points for overall, batting, or bowling categories.")
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject().apply {
                            put("category", JSONObject().apply {
                                put("type", "string")
                                put("description", "Category: OVERALL, BATTING, or BOWLING")
                            })
                        })
                        put("required", JSONArray().put("category"))
                    })
                })
            })
            put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "getMatchList")
                    put("description", "Searches and lists matches filtered by status, team name, venue, or date (e.g. '3rd October', '03 Oct').")
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject().apply {
                            put("status", JSONObject().apply {
                                put("type", "string")
                                put("description", "Match status filter: FINISHED, LIVE, or ABANDONED")
                            })
                            put("teamName", JSONObject().apply {
                                put("type", "string")
                                put("description", "Optional team name to filter")
                            })
                            put("venue", JSONObject().apply {
                                put("type", "string")
                                put("description", "Optional venue name to filter")
                            })
                            put("dateQuery", JSONObject().apply {
                                put("type", "string")
                                put("description", "Optional date string or month to search for matches (e.g. '3rd October' or '03 Oct')")
                            })
                        })
                    })
                })
            })
            put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "getMatchFullDetails")
                    put("description", "Gets detailed match summary, including scores, toss decision, rules, venue, and Player of the Match.")
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject().apply {
                            put("matchId", JSONObject().apply {
                                put("type", "string")
                                put("description", "The unique match ID")
                            })
                        })
                        put("required", JSONArray().put("matchId"))
                    })
                })
            })
            put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "getMatchScorecard")
                    put("description", "Gets individual player scorecard performance (runs, balls, 4s, 6s, wickets, overs, fielding) for a specific match ID.")
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject().apply {
                            put("matchId", JSONObject().apply {
                                put("type", "string")
                                put("description", "The unique match ID")
                            })
                        })
                        put("required", JSONArray().put("matchId"))
                    })
                })
            })
            put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "getMatchBreakdown")
                    put("description", "Gets ball-by-ball delivery list, commentary text, fall of wickets, or partnerships for a match innings.")
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject().apply {
                            put("matchId", JSONObject().apply {
                                put("type", "string")
                                put("description", "The unique match ID")
                            })
                            put("inningsNumber", JSONObject().apply {
                                put("type", "integer")
                                put("description", "Innings number: 1 or 2")
                            })
                            put("queryType", JSONObject().apply {
                                put("type", "string")
                                put("description", "Type of breakdown: BALL_BY_BALL, COMMENTARY, FALL_OF_WICKETS, or PARTNERSHIPS")
                            })
                        })
                        put("required", JSONArray().put("matchId"))
                    })
                })
            })
            put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "getBatterVsBowlerMatchup")
                    put("description", "Gets micro head-to-head performance of a batter against a specific bowler, or a complete breakdown against ALL bowlers if bowlerName is omitted or blank.")
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject().apply {
                            put("batterName", JSONObject().apply {
                                put("type", "string")
                                put("description", "Name of the batter")
                            })
                            put("bowlerName", JSONObject().apply {
                                put("type", "string")
                                put("description", "Optional name of the bowler. Leave blank or omit to get breakdown against all bowlers.")
                            })
                            put("inningsNumber", JSONObject().apply {
                                put("type", "integer")
                                put("description", "Optional innings filter: 1 or 2")
                            })
                            put("matchId", JSONObject().apply {
                                put("type", "string")
                                put("description", "Optional match ID to restrict query to a specific match")
                            })
                        })
                        put("required", JSONArray().put("batterName"))
                    })
                })
            })
            put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "getLiveMatchStatus")
                    put("description", "Gets current live match state, active striker, non-striker, active bowler, required runs, target, and free hit status.")
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject().apply {
                            put("matchId", JSONObject().apply {
                                put("type", "string")
                                put("description", "Optional specific live match ID")
                            })
                        })
                    })
                })
            })
            put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "getTeamRecordsAndStandings")
                    put("description", "Gets team win/loss records, win percentages, standings, and highest team scores.")
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject().apply {
                            put("teamName", JSONObject().apply {
                                put("type", "string")
                                put("description", "Optional specific team name")
                            })
                        })
                    })
                })
            })
            put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "getVenueAnalytics")
                    put("description", "Gets venue statistics including average 1st and 2nd innings scores, toss win percentage, and total matches played at the venue.")
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject().apply {
                            put("venueName", JSONObject().apply {
                                put("type", "string")
                                put("description", "Name of the venue")
                            })
                        })
                        put("required", JSONArray().put("venueName"))
                    })
                })
            })
            put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "searchCommentary")
                    put("description", "Searches commentary text, event logs, dropped catches, user commentary notes, highlights, and moments across all matches in the league by query keywords (e.g. 'chotu ne catch chhod diya', 'dropped catch', 'six', 'run out') or player name.")
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject().apply {
                            put("query", JSONObject().apply {
                                put("type", "string")
                                put("description", "Keywords or phrase to search in commentary text (e.g. 'catch chhod', 'dropped', 'chotu')")
                            })
                            put("playerName", JSONObject().apply {
                                put("type", "string")
                                put("description", "Optional player name filter")
                            })
                            put("matchId", JSONObject().apply {
                                put("type", "string")
                                put("description", "Optional specific match ID filter")
                            })
                        })
                        put("required", JSONArray().put("query"))
                    })
                })
            })
            put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "getPlayerDismissalAnalysis")
                    put("description", "Analyzes how a player gets out (modes of dismissal: Bowled, Caught, LBW, Run Out, Stumped, Hit Wicket), their nemesis bowler (bowler who dismissed them most), who caught them most, and their not-out percentage.")
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject().apply {
                            put("playerName", JSONObject().apply {
                                put("type", "string")
                                put("description", "Name or nickname of the player to analyze")
                            })
                        })
                        put("required", JSONArray().put("playerName"))
                    })
                })
            })
            put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "getPlayerStatsAgainstTeam")
                    put("description", "Gets a player's complete batting and bowling statistics specifically against an opposition team (runs, average, strike rate, boundaries, wickets, economy, and win/loss record).")
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject().apply {
                            put("playerName", JSONObject().apply {
                                put("type", "string")
                                put("description", "Name or nickname of the player")
                            })
                            put("opponentTeam", JSONObject().apply {
                                put("type", "string")
                                put("description", "Name or partial name of the opposition team")
                            })
                        })
                        put("required", JSONArray().put("playerName").put("opponentTeam"))
                    })
                })
            })
            put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "getPlayerPhaseOfPlayStats")
                    put("description", "Gets a player's batting and bowling performance broken down by cricket match phases: Powerplay (early overs), Middle overs, and Death overs (final overs).")
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject().apply {
                            put("playerName", JSONObject().apply {
                                put("type", "string")
                                put("description", "Name or nickname of the player")
                            })
                            put("phase", JSONObject().apply {
                                put("type", "string")
                                put("description", "Optional phase filter: ALL, POWERPLAY, MIDDLE, or DEATH")
                            })
                        })
                        put("required", JSONArray().put("playerName"))
                    })
                })
            })
            put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "getPlayerWinImpactStats")
                    put("description", "Analyzes a player's match-winning impact and contribution: team win percentage when they play vs overall win percentage, win rate when scoring 25+ runs or taking 2+ wickets, and Player of the Match awards.")
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject().apply {
                            put("playerName", JSONObject().apply {
                                put("type", "string")
                                put("description", "Name or nickname of the player")
                            })
                        })
                        put("required", JSONArray().put("playerName"))
                    })
                })
            })
            put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "getPlayerPersonalBests")
                    put("description", "Gets all career personal records and bests for a player: best batting innings (highest score with balls and SR), best bowling spell (e.g. 4/12), most boundaries in an innings, and best fielding performance.")
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject().apply {
                            put("playerName", JSONObject().apply {
                                put("type", "string")
                                put("description", "Name or nickname of the player")
                            })
                        })
                        put("required", JSONArray().put("playerName"))
                    })
                })
            })
            put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "compareTwoPlayers")
                    put("description", "Compares two cricket players side-by-side in batting, bowling, strike rates, averages, boundaries, wickets, economy, and their head-to-head matchup.")
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject().apply {
                            put("player1", JSONObject().apply {
                                put("type", "string")
                                put("description", "Name or nickname of the first player")
                            })
                            put("player2", JSONObject().apply {
                                put("type", "string")
                                put("description", "Name or nickname of the second player")
                            })
                        })
                        put("required", JSONArray().put("player1").put("player2"))
                    })
                })
            })
            put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "getPlayerBestPartnerships")
                    put("description", "Finds a player's top batting partnerships across all matches in the league (highest partnership runs, partner name, balls faced, and individual contributions).")
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject().apply {
                            put("playerName", JSONObject().apply {
                                put("type", "string")
                                put("description", "Name or nickname of the player")
                            })
                            put("partnerName", JSONObject().apply {
                                put("type", "string")
                                put("description", "Optional specific batting partner name to filter")
                            })
                        })
                        put("required", JSONArray().put("playerName"))
                    })
                })
            })
            put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "getPlayerInningsSplit")
                    put("description", "Analyzes a player's performance when setting a target (1st Innings) vs chasing a target (2nd Innings), including runs, average, strike rate, wickets, economy, and win percentage in chases.")
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject().apply {
                            put("playerName", JSONObject().apply {
                                put("type", "string")
                                put("description", "Name or nickname of the player")
                            })
                        })
                        put("required", JSONArray().put("playerName"))
                    })
                })
            })
            put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "getPlayerFormStreaks")
                    put("description", "Calculates a player's current and best career streaks (consecutive matches scoring 20+/30+, consecutive games with wickets taken, consecutive boundary matches) and upcoming milestone progress.")
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject().apply {
                            put("playerName", JSONObject().apply {
                                put("type", "string")
                                put("description", "Name or nickname of the player")
                            })
                        })
                        put("required", JSONArray().put("playerName"))
                    })
                })
            })
        }
    }
}
