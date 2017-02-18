#!/usr/bin/python

import sys
import urllib.request

dataDir = "/Users/robertrtung/Documents/Yale Junior Year/HackNYU/Data/"

def findLinks(str):
	links = []
	# maxLinks = 100
	i = 0

	while i < len(str):
		if str[i:i+5] == 'src=\"':
			add = ""
			newi = len(str) - 1

			for j in range(i+5,len(str)):
				if str[j] == '\"':
					newi = j
					break

			links.append(str[i+5:newi])

			i = newi
		else:
			i += 1

	return links

def findLinksInList(strlist):
	links = []
	curTotalLinks = 0
	maxLinks = 100

	for str in strlist:
		lin = findLinks(str)
		links = links + lin
		curTotalLinks += len(lin)

		if curTotalLinks >= maxLinks:
			break

	return links


def main():
	if len(sys.argv) < 2:
		sys.exit(1)

	if sys.argv[1] != 'good' and sys.argv[1] != 'bad':
		sys.exit(1)

	with open(dataDir + sys.argv[1] + "photoscrape.html") as f:
		content = f.readlines()
	links = findLinksInList(content)

	# target = open("/Users/robertrtung/Documents/Yale Junior Year/HackNYU/Data/badphotolinks", 'w')
	i = 1
	for link in links:
		# target.write(link + '\n')
		try:
			urllib.request.urlretrieve(link, sys.argv[1] + "pic" + str(i) + ".jpg")
			i += 1
		except ValueError:
			print("Oops!  Link " + str(i) + " of " + sys.argv[1] + " was unscrapable")


if __name__ == "__main__":
    main()