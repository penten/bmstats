(ns bmstats.core
  (:gen-class)
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.java.jdbc.sql :as sql])
  (:import (java.io File) (java.util Calendar)
           (org.jfree.data.time TimeSeries TimeSeriesCollection Hour)
           (org.jfree.chart ChartFactory ChartUtilities)))


;; ===== database credentials =====

(def messages-db-path (System/getenv "BMSTATS_MESSAGES_DB"))
(def stats-db-path (System/getenv "BMSTATS_STATS_DB"))

(def messages-db {
            :subprotocol "sqlite"
            :subname messages-db-path})
(def stats-db {
             :subprotocol "sqlite"
             :subname stats-db-path
             :create true})

;; ===== utility functions =====

(defn cal->int [cal]
  (/ (.getTimeInMillis cal) 1000))

(defn int->cal [i]
  (doto
    (Calendar/getInstance)
    (.setTimeInMillis (* 1000 i))))

(defn int->date [i]
    (.getTime (int->cal i)))

(defn current-hour []
  "Creates a calendar truncated to the last hour: 3:27 => 3:00, 4:01 => 4:00.."
  (doto
    (Calendar/getInstance)
    (.set Calendar/MINUTE 0)
    (.set Calendar/SECOND 0)
    (.set Calendar/MILLISECOND 0)))

(defn current-day []
  "Creates a calendar truncated to the last hour: 3:27 => 3:00, 4:01 => 4:00.."
  (doto
    (current-hour)
    (.set Calendar/HOUR 0)))

(defn since [cal period-type period]
  "Returns a calendar n days, hours, etc (period-type) after cal.
  Period can be negative"
  (doto
    (Calendar/getInstance)
    (.setTime (.getTime cal))
    (.add period-type period)))

(defn extract-counts [s]
  (zipmap (map #(str "msg_" (:objecttype %)) s) (map :count s)))

;; ===== statistics gathering =====

(defn datafile-size []
  "Size in bytes of the messages.dat datafile"
(.length (File. messages-db-path)))

(defn count-messages [from period-type period]
  "Messages between from and period ago"
  (let [to (since from period-type (- period))]
    (jdbc/query messages-db
                [(str
                  "SELECT objecttype, COUNT(*) count "
                  "FROM inventory "
                  "WHERE receivedtime < ? AND receivedtime > ? "
                  "GROUP BY objecttype")
                 (cal->int from) (cal->int to)])))

(defn count-pubkeys []
  "Total number of pubkeys in DB"
    (first (jdbc/query messages-db ["SELECT COUNT(*) total FROM pubkeys"])))

(defn gather-stats []
  "Combine message counts, datafile size, pubkey counts in a single map"
  (let [stats (extract-counts (count-messages (current-hour) Calendar/HOUR 1))
        pkeys (:total (count-pubkeys))
        fsize (datafile-size)
        stats (assoc stats
                "total_pubkeys" pkeys
                "datafile_size" fsize
                "time" (cal->int (current-hour)))]
    stats))


;; ===== statistics recording =====

(defn save-stats [stats]
  "Insert map of gathered stats into database"
  (jdbc/insert! stats-db :hourly_stats stats))

;; ===== report generation =====

(defn load-stats [from to]
  "Load stats between the given dates from database"
  (jdbc/query stats-db
              [(str
                "SELECT *
                FROM hourly_stats
                WHERE time > ? AND time < ?
                ORDER BY time ASC")
               from to]))

(defn combine-stats [coll]
  "Given a list of maps, adds the msg counts within.
  Values that are not message counts are set to those of the first map"
  (let [base (first coll)
        keyf #(== 0 (.indexOf (str %) ":msg"))
        msgkeys (filter keyf (keys base))
        othkeys (filter (complement keyf) (keys base))
        msgmap (select-keys (apply merge-with + coll) msgkeys)]
    (conj (select-keys base othkeys) msgmap)))

(defn hourly-stats [days]
  "Return stats of every hour in the last n days"
  (let
    [to (current-day)
     from (since to Calendar/DAY_OF_YEAR (- days))]
    (load-stats (cal->int from) (cal->int to))))

(defn daily-stats [days]
  "Return stats of every day in the last n days"
  (map combine-stats (partition 24 (hourly-stats days))))

(defn generate-graph-dataset [data selector]
  (let [series (TimeSeries. "Data")
        ds (TimeSeriesCollection.)]
    (doseq [x data] (.add series (Hour. (int->date (:time x))) (selector x)))
    (.addSeries ds series)
    ds))

(defn generate-graph [data selector filename]
  "Generate a graph showing the data selected from the loaded stats"
  (let [dataset (generate-graph-dataset data selector)
        chart (ChartFactory/createTimeSeriesChart
               "Chartname" "date" "message count"
               dataset true true false)]
    (ChartUtilities/saveChartAsPNG (File. filename) chart 500 500)))

;; ===== main =====

(defn -main []
  (save-stats (gather-stats))
  (generate-graph (hourly-stats 1) :msg_msg "today.png")
  (generate-graph (hourly-stats 7) :msg_msg "week.png")
  (generate-graph (daily-stats 7) :msg_msg "week.png"))


;; PyBitmessage's class_singleCleaner.py has been changed to flush to disk more frequently
;; (defaults to 60 minutes)

;; DB sql
;; "CREATE TABLE IF NOT EXISTS hourly_stats (time INTEGER PRIMARY KEY, datafile_size INTEGER DEFAULT 0, total_pubkeys INTEGER DEFAULT 0, msg_pubkey INTEGER DEFAULT 0, msg_msg INTEGER DEFAULT 0, msg_getpubkey INTEGER DEFAULT 0, msg_broadcast INTEGER DEFAULT 0)")

