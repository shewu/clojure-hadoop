(ns clojure-hadoop.job
  (:require [clojure-hadoop.gen :as gen]
            [clojure-hadoop.imports :as imp]
            [clojure-hadoop.wrap :as wrap]
            [clojure-hadoop.config :as config]))

(imp/import-io)
(imp/import-io-compress)
(imp/import-fs)
(imp/import-mapred)

(gen/gen-job-classes)
(gen/gen-main-method)

(def *jobconf* nil)

(def #^{:private true} method-fn-name
     {"map" "mapper-map"
      "reduce" "reducer-reduce"})

(def #^{:private true} wrapper-fn
     {"map" wrap/wrap-map
      "reduce" wrap/wrap-reduce})

(defn configure-functions
  "Preps the mapper or reducer with a Clojure function read from the
  job configuration.  Called from Mapper.configure and
  Reducer.configure."
  [type jobconf]
  (alter-var-root (var *jobconf*) (fn [_] jobconf))
  (let [[ns-name fn-name] (.split (.get jobconf (str "clojure-hadoop.job." type)) "/")]
    (when-not (find-ns (symbol ns-name))
      (require (symbol ns-name)))
    (assert (find-ns (symbol ns-name)))
    (let [function (deref (resolve (symbol ns-name fn-name)))]
      (assert (fn? function))
      (alter-var-root (ns-resolve (the-ns 'clojure-hadoop.job)
                                  (symbol (method-fn-name type)))
                      (fn [_] ((wrapper-fn type) function))))))

;;; MAPPER

(defn mapper-configure [this jobconf]
  (configure-functions "map" jobconf))

(defn mapper-map [this wkey wvalue output reporter]
  (throw (Exception. "Mapper function not defined.")))

;;; REDUCER

(defn reducer-configure [this jobconf]
  (configure-functions "reduce" jobconf))

(defn reducer-reduce [this wkey wvalues output reporter]
  (throw (Exception. "Reducer function not defined.")))

;;; TOOL 

(defn parse-command-line [jobconf args]
  (try
   (config/parse-args jobconf args)
   (catch Exception e
     (prn e)
     (config/print-usage)
     (System/exit 1))))

(defn handle-replace-option [jobconf]
  (when (= "true" (.get jobconf "clojure-hadoop.job.replace"))
    (let [fs (FileSystem/get jobconf)
          output (FileOutputFormat/getOutputPath jobconf)]
      (.delete fs output true))))

(defn tool-run [this args]
  (doto (JobConf. (.getConf this) (.getClass this))
    (.setJobName "clojure_hadoop.job")
    (.setOutputKeyClass Text)
    (.setOutputValueClass Text)
    (.setMapperClass (Class/forName "clojure_hadoop.job_mapper"))
    (.setReducerClass (Class/forName "clojure_hadoop.job_reducer"))
    (.setInputFormat SequenceFileInputFormat)
    (.setOutputFormat SequenceFileOutputFormat)
    (FileOutputFormat/setCompressOutput true)
    (SequenceFileOutputFormat/setOutputCompressionType
     SequenceFile$CompressionType/BLOCK)
    (parse-command-line args)
    (handle-replace-option)
    (JobClient/runJob))
  0)
