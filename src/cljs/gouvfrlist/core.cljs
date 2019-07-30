;; Copyright (c) 2019 DINSIC, Bastien Guerry <bastien.guerry@data.gouv.fr>
;; SPDX-License-Identifier: EPL-2.0
;; License-Filename: LICENSES/EPL-2.0.txt

(ns gouvfrlist.core
  (:require [cljs.core.async :as async]
            [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [reagent.session :as session]
            [cljs-bean.core :refer [bean]]
            [ajax.core :refer [GET POST]]
            [goog.string :as gstring]
            [goog.string.format]
            [markdown-to-hiccup.core :as md]))

(def items-per-page 100)

(re-frame/reg-event-db
 :initialize-db!
 (fn [_ _]      
   {:websites      nil
    :sort-by       nil
    :websites-page 0
    :view          :list
    :reverse-sort  true
    :search-filter ""}))

(re-frame/reg-event-db
 :update-websites!
 (fn [db [_ websites]]
   (if websites (assoc db :websites websites))))

(re-frame/reg-event-db
 :websites-page!
 (fn [db [_ n]] (assoc db :websites-page n)))

(re-frame/reg-sub
 :websites-page?
 (fn [db _] (:websites-page db)))

(re-frame/reg-event-db
 :search-filter!
 (fn [db [_ s]]
   (re-frame/dispatch [:websites-page! 0])
   (assoc db :search-filter s)))

(re-frame/reg-event-db
 :view!
 (fn [db [_ view]]
   (re-frame/dispatch [:search-filter! ""])
   (assoc db :view view)))

(re-frame/reg-sub
 :search-filter?
 (fn [db _] (:search-filter db)))

(re-frame/reg-sub
 :sort-by?
 (fn [db _] (:sort-by db)))

(re-frame/reg-event-db
 :sort-by!
 (fn [db [_ k]]
   (re-frame/dispatch [:websites-page! 0])
   (when (= k (:sort-by db))
     (re-frame/dispatch [:reverse-sort!]))
   (assoc db :sort-by k)))

(re-frame/reg-sub 
 :reverse-sort?
 (fn [db _] (:reverse-sort db)))

(re-frame/reg-event-db
 :reverse-sort!
 (fn [db _] (assoc db :reverse-sort (not (:reverse-sort db)))))

(defn apply-search-filter [m s]
  (filter #(re-find
            (re-pattern (str "(?i)" s))
            (gstring/format
             "%s %s %s" (:url %) (:title %) (:description %)))
          m))

(re-frame/reg-sub
 :websites?
 (fn [db _]
   (let [w0 (:websites db)
         w  (case @(re-frame/subscribe [:sort-by?])
              :name  (sort-by :url w0)
              :size  (sort-by :content-length w0)
              :tags  (sort-by :tags w0)
              :reqs  (sort-by :requests-number w0)
              :https (sort-by :is-secure? w0)
              :ga    (sort-by :using-ga? w0)
              (shuffle w0))]
     (apply-search-filter
      (if @(re-frame/subscribe [:reverse-sort?])
        (reverse w)
        w)
      @(re-frame/subscribe [:search-filter?])))))

(re-frame/reg-sub
 :view?
 (fn [db _] (:view db)))

(defn md-to-string [s]
  (-> s (md/md->hiccup) (md/component)))

(defn format-content-length [cl]
  (str (gstring/format
        "%.2f" (/ cl 1000000.0))
       " MO"))

(defn fa
  ([s]
   [:span {:class (str "icon")}
    [:i {:class (str "fas " s)}]])
  ([s has-text-info?]
   [:span {:class (str "icon " has-text-info?)}
    [:i {:class (str "fas " s)}]]))

(def search-filter-chan (async/chan 10))

(defn start-search-filter-loop []
  (async/go
    (loop [s (async/<! search-filter-chan)]
      (re-frame/dispatch [:search-filter! s])
      (recur (async/<! search-filter-chan)))))

(defn websites-list []
  [:table {:class "table is-hoverable is-fullwidth"}
   [:thead
    [:tr
     [:th [:a {:class    "button"
               :title    "Trier par ordre alphabétique"
               :on-click #(re-frame/dispatch [:sort-by! :name])} "Titre"]]
     [:th [:a {:class    "button"
               :title    "Trier par taille de contenus téléchargés"
               :on-click #(re-frame/dispatch [:sort-by! :size])} "Taille"]]
     [:th [:a {:class    "button"
               :title    "Trier par nombre de balises HTML"
               :on-click #(re-frame/dispatch [:sort-by! :tags])} "Balises"]]
     [:th [:a {:class    "button"
               :title    "Trier par nombre de requêtes"
               :on-click #(re-frame/dispatch [:sort-by! :reqs])} "Requêtes"]]
     [:th [:a {:class    "button"
               :title    "Le site est-il accessible en https?"
               :on-click #(re-frame/dispatch [:sort-by! :https])}  "https?"]]
     [:th [:a {:class    "button"
               :title    "Le site utilise-t-il Google Analytics?"
               :on-click #(re-frame/dispatch [:sort-by! :ga])}  "GA?"]]]]
   [:tbody
    (for [{:keys [title url description content-length og:image
                  requests-number is-secure? using-ga? tags]
           :as   w}
          (take items-per-page
                (drop (* items-per-page @(re-frame/subscribe [:websites-page?]))
                      @(re-frame/subscribe [:websites?])))]
      ^{:key w}
      [:tr
       [:td [:a {:href url :target "new" :title (str title " " description)} url]]
       [:td (format-content-length content-length)]
       [:td tags]
       [:td requests-number]
       [:td (fa "fa-lock"
                (if is-secure? "has-text-success"
                    "has-text-danger"))]
       [:td (fa "fa-user-secret"
                (if using-ga? "has-text-danger"
                    "has-text-success"))]])]])

(defn websites-cards []
  (into
   [:div]
   (for [ww (partition-all
             3 (take items-per-page
                     (drop (* items-per-page @(re-frame/subscribe [:websites-page?]))
                           @(re-frame/subscribe [:websites?]))))]
     ^{:key ww}
     [:div {:class "columns"}
      (for [{:keys [capture-filename url title description og:image
                    tags requests-number content-length is-secure?
                    using-ga?]
             :as   w} ww]
        (let [t (if title (pr-str title)
                    (if description
                      (pr-str description)))]
          ^{:key w}
          [:div {:class "column is-4"}
           [:div {:class "card"}
            [:div {:class "card-image"}
             [:figure {:class "image is-4by3"}
              [:a {:class  "title is-4"
                   :target "new"
                   :title  t
                   :href   url}
               [:img {:src (str "screenshots/" capture-filename)}]]]]
            [:div {:class "card-content"}
             [:div {:class "media"}
              (if og:image
                [:div {:class "media-left"}
                 [:figure {:class "image is-48x48"}
                  [:img {:src og:image}]]])
              [:div {:class "media-content"}
               [:p [:a {:class  "title is-4"
                        :target "new"
                        :title  t
                        :href   url} url]]
               [:br]
               [:p {:class "subtitle is-6"}
                (str (subs description 0 150) "...")]]]]
            [:div {:class "card-footer"}
             [:div {:class "card-footer-item"
                    :title (if is-secure?
                             "Le site utilise https."
                             "Le site n'utilise pas https.")}
              (fa "fa-lock"
                  (if is-secure? "has-text-success"
                      "has-text-danger"))]
             [:div {:class "card-footer-item"
                    :title (if using-ga?
                             "Le site utilise Google Analytics."
                             "Le site n'utilise pas Google Analytics.")}
              (fa "fa-user-secret"
                  (if using-ga?
                    "has-text-danger"
                    "has-text-success"))]
             [:div {:class "card-footer-item"
                    :title "Taille téléchargée"}
              (fa "fa-download")
              (format-content-length content-length)]
             [:div {:class "card-footer-item"
                    :title "Nombre de requêtes"}
              (fa "fa-exchange-alt")
              requests-number]
             [:div {:class "card-footer-item"
                    :title "Nombre de tags"}
              (fa "fa-code")
              tags]]]]))])))

(defn about-page []
  [:div
   [:div {:class "container"}
    [:h1 {:class "title"} "Pourquoi ?"]
    (md-to-string
     "Ce site propose de chercher dans les titre et les descriptions des sites de l'administration publique.  Une première liste de ces sites est [disponible ici](https://gist.github.com/bzg/08e7a8651533057e278a10a580a7a1e0) ; le site utilise une [version corrigée](https://github.com/bzg/gouvfrlist/blob/master/tested.gouv.fr.txt) de cette liste, sans les sites indisponibles.  Le but est de voir si une telle liste peut être utile à d'autres.")
    [:br]
    [:h1 {:class "title"} "Quelles données sont présentées ?"]
    (md-to-string
     "Pour chaque site nous indiquons (1) la taille des contenus téléchargés (2) le nombre de requêtes faites par le site vers des serveurs pour télécharger ces contenus (3) le nombre de balises (4) si le site utilise correctement une connexion sécurisée avec https (5) et si le site utilise Google Analytics ou non. Vous pouvez **télécharger** ces données [ici](/all.json).  Une partie de ces données correspond à celle utilisées par [ecoindex](http://www.ecoindex.fr/quest-ce-que-ecoindex/).")
    [:br]
    [:h1 {:class "title"} "La suite ?"]
    (md-to-string
     "Ce site est **expérimental**.  Les informations qu'il présente peuvent être erronnées.  N'hésitez pas à me [signaler](https://github.com/bzg/gouvfrlist/issues) tout problème constaté ou à proposer des améliorations.  Vous pouvez aussi m'envoyer [un mail](mailto:bzg@bzg.fr).  Merci pour vos retours !")]])

(defn change-page [next]
  (let [websites-page @(re-frame/subscribe [:websites-page?])
        count-pages   (count (partition-all
                              items-per-page
                              @(re-frame/subscribe [:websites?])))]
    (cond
      (= next "first")
      (re-frame/dispatch [:websites-page! 0])
      (= next "last")
      (re-frame/dispatch [:websites-page! (dec count-pages)])
      (and (< websites-page (dec count-pages)) next)
      (re-frame/dispatch [:websites-page! (inc websites-page)])
      (and (> websites-page 0) (not next))
      (re-frame/dispatch [:websites-page! (dec websites-page)]))))

(defn main-page []
  (let [websites-page  @(re-frame/subscribe [:websites-page?])
        count-pages    (count (partition-all
                               items-per-page @(re-frame/subscribe [:websites?])))
        first-disabled (= websites-page 0)
        last-disabled  (= websites-page (dec count-pages))]
    [:div
     [:div {:class "level-left"}
      
      [:div {:class "level-item"}
       [:span {:title    "Voir sous forme de liste"
               :on-click #(re-frame/dispatch [:view! :list])} (fa "fa-list")]]
      [:div {:class "level-item"}
       [:span {:title    "Voir avec les captures d'écran"
               :on-click #(re-frame/dispatch [:view! :cards])} (fa "fa-image")]]
      [:div {:class "level-item"}
       [:input {:class       "input"
                :size        100
                :placeholder "Recherche libre"
                :on-change   (fn [e]                           
                               (let [ev (.-value (.-target e))]
                                 (async/go (async/>! search-filter-chan ev))))}]]
      [:nav {:class "pagination level-item" :role "navigation" :aria-label "pagination"}
       [:a {:class    "pagination-previous"
            :on-click #(change-page "first")
            :disabled first-disabled}
        (fa "fa-fast-backward")]
       [:a {:class    "pagination-previous"
            :on-click #(change-page nil)
            :disabled first-disabled}
        (fa "fa-step-backward")]
       [:a {:class    "pagination-next"
            :on-click #(change-page true)
            :disabled last-disabled}
        (fa "fa-step-forward")]
       [:a {:class    "pagination-next"
            :on-click #(change-page "last")
            :disabled last-disabled}
        (fa "fa-fast-forward")]]
      [:div {:class "level-item"}
       [:span {:title    "À propos"
               :on-click #(re-frame/dispatch [:view! :about])} (fa "fa-question")]]]
     [:br]
     (case @(re-frame/subscribe [:view?])
       :cards [websites-cards]
       :list  [websites-list]
       :about [about-page])]))

(defn main-class []
  (reagent/create-class
   {:component-will-mount
    (fn []
      (GET "http://localhost:3000/all" :handler
           #(re-frame/dispatch
             [:update-websites! (map (comp bean clj->js) %)])))
    :reagent-render main-page}))

(defn ^:export init []
  (re-frame/clear-subscription-cache!)
  (re-frame/dispatch-sync [:initialize-db!])
  (start-search-filter-loop)
  (reagent/render
   [main-class]
   (. js/document (getElementById "app"))))
