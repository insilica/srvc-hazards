#!/usr/bin/env bb

(require '[babashka.deps :as deps]
         '[babashka.fs :as fs])

(deps/add-deps '{:deps {co.insilica/bb-srvc
                        {:local/root "../bb-srvc"}
                        #_{:mvn/version "0.9.0-SNAPSHOT"}}})

(require '[srvc.bb.html :as bhtml])

(bhtml/serve (-> *file* fs/parent fs/parent (fs/path "resources"))
             "public/rank.html")
