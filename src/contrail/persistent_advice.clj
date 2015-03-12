(ns contrail.persistent-advice
  (:require [richelieu.core :as richelieu]))

(defonce ^:private advised-vars (atom #{}))

(defn all-advised []
  @advised-vars)

(defn current-advised []
  richelieu/*current-advised*)

(def ^:private ^:dynamic *suppress-readvising?*
  "Used to prevent infinite recursion when re-advising a var after it
  has been re-defined by the user"
  false)

(defn get-our-advising-fn
  "Of the various Richelieu advising functions which may advice `f`, a
  var, returns the first (and hopefully only) one created by
  Contrail."
  [f]
  ;; The deref is necessary because Richeleu actually sets the advice
  ;; on the function, not the var
  (first (filter (comp :traced-by-contrail meta) (richelieu/advice (deref f)))))

(defn unadvise* [f]
  (binding [*suppress-readvising?* true]
    (richelieu/unadvise-var f (get-our-advising-fn f)))
  (swap! advised-vars disj f))

(defn unadvise [f]
  (remove-watch f "contrail")
  (unadvise* f))

(defn advise* [f advice-fn]
  (binding [*suppress-readvising?* true]
    (richelieu/advise-var f advice-fn))
  (swap! advised-vars conj f))

(defn get-watcher [advice-fn]
  (fn [_ advised-var _ new-var-value]
    (when-not *suppress-readvising?*
      (binding [*suppress-readvising?* true]
        (println advised-var "changed, re-tracing.")
        (unadvise* advised-var)
        (advise* advised-var advice-fn)))))

(defn add-metadata [advice-fn]
  (with-meta advice-fn {:traced-by-contrail true}))

(defn wrap-with-untraced-guard [advice-fn]
  (fn [f & args]
    (if ((all-advised) (current-advised))
      (apply advice-fn f args)
      (do
        (unadvise* (current-advised))
        (apply f args)))))

(defn advise [f advice-fn]
  (let [final-advice-fn (-> advice-fn
                            wrap-with-untraced-guard
                            add-metadata)]
    (advise* f final-advice-fn)
    (add-watch f "contrail" (get-watcher final-advice-fn))))

