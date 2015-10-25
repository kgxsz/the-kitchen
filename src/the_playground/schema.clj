(ns the-playground.schema
  (:require [schema.core :as s]))

(s/defschema User {:id s/Int
                   :name s/Str})

(s/defschema Article {:id s/Int
                      :title s/Str
                      :text s/Str})

(s/defschema UsersResponse {:users [User]})

(s/defschema CreateUserResponse {:user User})

(s/defschema ArticlesResponse {:articles [Article]})

