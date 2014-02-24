(ns pallet.core.file-upload.rsync-upload
  "Implementation of file upload using rsync."
  (:require
   [clojure.tools.logging :refer [debugf]]
   [pallet.action :refer [effective-user]]
   [pallet.actions.direct.rsync
    :refer [default-options rsync-command]]
   [pallet.local.execute :refer [local-checked-script]]
   [pallet.session :as session]
   [pallet.user :refer [effective-username]]
   [pallet.core.file-upload :refer [file-uploader]]
   [pallet.core.file-upload.protocols :refer [FileUpload]]
   [pallet.node :refer [ssh-port]]
   [pallet.ssh.file-upload.sftp-upload :refer [target]]
   [pallet.target :as target]))

(defn rsync-upload-file
  [local-path target-path address port username options]
  (debugf "rsync-upload-file %s:%s:%s from %s"
          address port target-path local-path)
  (local-checked-script
   "rsync file to target"
   ~(rsync-command
     local-path target-path
     username
     address port
     options)))


(defrecord RsyncUpload [upload-root]
  FileUpload
  (upload-file-path [_ target-path action-options]
    (target upload-root action-options target-path))
  (user-file-path [_ target-path action-options]
    (target upload-root action-options target-path))
  (upload-file
    [_ session local-path target-path action-options]
    (rsync-upload-file
     local-path
     (target
      upload-root
      (effective-username
       (effective-user (session/user session) action-options))
      target-path)
     (target/primary-ip (session/target session))
     (target/ssh-port (session/target session))
     (effective-username
      (effective-user (session/user session) action-options))
     (merge (-> (default-options session)
                (select-keys [:rsync-path]))
            {:chmod "go-w,go-r"}))))

(defn rsync-upload
  "Create an instance of the rsync upload strategy."
  [{:keys [upload-root] :as options}]
  (map->RsyncUpload (merge {:upload-root "/tmp"} options)))

(defmethod file-uploader :rsync
  [_ options]
  (rsync-upload options))
