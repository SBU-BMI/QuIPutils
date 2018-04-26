import csv
import json
import yaml
import random
import sys 
import os 
import glob
import argparse
import uuid
import datetime

from pymongo import MongoClient
from pymongo.errors import ConnectionFailure
from pymongo.errors import ConfigurationError

parser = argparse.ArgumentParser(description="Overlay loader.")
parser.add_argument("--quip",required=True,type=str,metavar="<folder>",help="QuIP results folder name.")
parser.add_argument("--images",required=True,type=str,metavar="<folder>",help="Images folder name.")
parser.add_argument("--overlays",required=True,type=str,metavar="<folder>",help="Overlays folder name.")
parser.add_argument("--color",required=True,type=int,metavar="value",help="Starting color.")
parser.add_argument("--subfolders",required=True,type=int,metavar="value",help="Should be 1, if the results folder has multiple result sets in subfolders.")
args = {}

def quipdb_connect(dbhost,dbport):
    dburi = "mongodb://"+dbhost+":"+str(dbport)+"/"
    client = MongoClient(dburi)
    try:
       res = client.admin.command('ismaster')
    except ConnectionFailure:
       print("Server is not available.")
       return None
    return client

def quipdb_getdb(client,dbname):
    db = client[dbname]
    return db

def get_analysis_folders(root_folder,subfolders):
    analysis_folders = []
    if int(subfolders) == 0:
       analysis_folders.append(root_folder)
    else: 
       folder_names = root_folder + "/*"
       for name in glob.glob(folder_names):
           analysis_folders.append(name)
    return analysis_folders

def get_metadata(analysis_folder):
    fnames = analysis_folder + "/*-algmeta.json"
    mdata = {}
    for name in glob.glob(fnames):
        mdata = read_metadata(name) 
        break
    return mdata

def read_metadata(meta_file):
    mf = open(meta_file)
    data = json.load(mf)
    return data

def set_metadata(mdata,tile_folder):
    mdoc = {"color" : "yellow"}
    mdoc["title"] = mdata["analysis_desc"]
    imgdoc = {"case_id" : mdata["case_id"], "subject_id" : mdata["subject_id"] }
    mdoc["image"] = imgdoc
    provdoc = {"study_id" : ""}
    provdoc["analysis_execution_id"] = mdata["analysis_id"]
    provdoc["type"] = "computer"
    provdoc["algorithm_params"] = mdata
    provdoc["randval"] = random.random()
    provdoc["submit_date"] = datetime.datetime.utcnow()
    mdoc["provenance"] = provdoc
    mdoc["tile-location"] = tile_folder
    mdoc["tile_name"] = mdata["case_id"]
    return mdoc

def get_input_file(mdata,images_folder):
    case_id = mdata["case_id"]
    fnames  = images_folder + "/" + case_id + "*"
    input_file = None 
    for name in glob.glob(fnames):
        input_file = name
        break
    return input_file

if __name__ == "__main__":
   args = vars(parser.parse_args())
   f = open("/home/QuIPutils/python/config.yml")
   config_data = yaml.load(f)
   analysis_folders = get_analysis_folders(args["quip"],int(args["subfolders"]))
   tiler_cmd = config_data["tiler_cmd"]
   root_out_folder = args["overlays"]
   color_idx = int(args["color"])
   for mfolder in analysis_folders:
       mdata = get_metadata(mfolder)
       case_id     = mdata["case_id"]
       subject_id  = mdata["subject_id"]
       analysis_id = mdata["analysis_id"]
       
       folder_extension = str(uuid.uuid5(uuid.NAMESPACE_X500,str(analysis_id)))
       out_folder = root_out_folder + "/" + subject_id + "/" + case_id + "." + folder_extension
       if not os.path.exists(out_folder):
          os.makedirs(out_folder)

       input_file = get_input_file(mdata,args["images"])

       cmd = tiler_cmd
       cmd = cmd + " " + mfolder + " " + out_folder  
       cmd = cmd + " " + input_file + " " + case_id
       cmd = cmd + " " + str(color_idx)
       os.system(cmd)

       provdoc = set_metadata(mdata,out_folder)

       client = quipdb_connect(config_data["dbhost"],config_data["dbport"])
       db = quipdb_getdb(client,config_data["dbname"])
       query = {}
       query["image.case_id"] = case_id
       query["image.subject_id"] = subject_id
       query["provenance.analysis_execution_id"] = analysis_id
       qres = db.metadata.find_one(query)
       if qres is None:
          db.metadata.insert_one(provdoc)
       else:
          db.metadata.update_one({'_id': qres['_id']}, {'$set': {'tile-location': provdoc["tile-location"]}}, upsert=False) 
       
       color_idx = color_idx + 1 
