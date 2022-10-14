(ns srvc-hazards
 (:require [clojure.java.io :as io]
           [clojure.string :as str]
           [srvc.bb :as sb]
           [tech.v3.dataset :as ds]
           tech.v3.libs.parquet))

(defn parse-ghs-data [s]
  (when-not (str/blank? s)
    (-> s
        str/trim
        (str/split #"   "))))

(defn filter-by-cas [dataset numbers]
  (-> dataset
      (ds/filter-column "Substance Number" (set numbers))
      (ds/filter-column "Number type" #{"CAS Number"})))

(defn substance-name [dataset cas-number]
  (-> dataset
      (filter-by-cas [cas-number])
      (ds/column "Substance Name")
      (->> (into [])
           frequencies
           (sort-by (fn [[k v]] [(- v) k]))
           ffirst)))

(defn ghs-sources [dataset cas-number]
  (-> dataset
      (filter-by-cas [cas-number])
      (ds/filter-column "GHS data" not-empty)
      ds/rows
      (->> (reduce
            #(update % (get %2 "Source") (fnil conj []) (get %2 "Result link"))
            {}))))

(defn hazards-by-cas [dataset numbers]
  (let [subset (filter-by-cas dataset numbers)]
    (reduce
     #(-> (filter-by-cas subset [%2])
           (ds/column "GHS data")
           (->> (into [])
                (mapcat parse-ghs-data)
                (into #{})
                (assoc % %2)))
     {}
     numbers)))

(defn generate-ranking [{:keys [cas-file]}]
  (let [numbers (->> cas-file io/file io/reader line-seq
                     (remove str/blank?)
                     (map str/trim))
        echem (ds/->dataset "echemportal/data/echemportal.parquet")
        subset (filter-by-cas echem numbers)
        ranked (->> numbers
                    (hazards-by-cas subset)
                    (mapv (fn [[k v]]
                            {:cas-number k
                             :hazard-sources (ghs-sources subset k)
                             :hazards (->> v
                                           (sort-by #(some-> % (str/split #"\s") first parse-long))
                                           vec)
                             :substance-name (substance-name subset k)}))
                    (sort-by #(do [(count (:hazards %))
                                   (:cas-number %)]))
                    vec)]
    (sb/generate
     [{:data {:ranking ranked}
       :type "document"}])))

(comment
  (def echem
    (ds/->dataset "echemportal/data/echemportal.parquet"))

  (hazards-by-cas echem ["13-16-1" "50-00-0" "50-06-6" "1696260-07-7"])
  (substance-name echem "50-00-0")

  (->> ["7007 : Flam. Gas 1A   7013 : Press. Gas (Liq.)   26787049 : Acute Tox. 4 - Oral   26767048 : Acute Tox. 3 - Dermal   26777047 : Acute Tox. 2 - Inhalation   7054 : Skin Irrit. 2   7057 : Eye Irrit. 2A   7060 : Resp. Sens. 1   7061 : Skin Sens. 1   7064 : Muta. 2   7065 : Carc. 1A   7071 : STOT Single Exp. 1   7075 : STOT Rep. Exp. 1   7080 : Aquatic Acute 2  "
        "26787048 : Acute Tox. 3 - Oral   26767048 : Acute Tox. 3 - Dermal   26777048 : Acute Tox. 3 - Inhalation   7052 : Skin Corr. 1B   7061 : Skin Sens. 1   7064 : Muta. 2   7066 : Carc. 1B  "]
       (mapv parse-ghs-data))
  )
