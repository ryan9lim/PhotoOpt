library("caret")

train <- read.csv("/Users/robertrtung/Documents/Yale Junior Year/HackNYU/Data/AnalysisData.csv", header = TRUE)

trainScore <- rep(NA, nrow(train))

for(i in 1:nrow(train)) {
  if(train[i,1] == 'good') {
    trainScore[i] = 1
  } else {
    trainScore[i] = 0
  }
}

trainScoreDF <- data.frame(score = trainScore)

# If NA, just give the average value
for(i in 3:10) {
  m <- mean(train[which(!is.na(train[,i])&(train[,i] != 0)),i])
  train[which(is.na(train[,i])),i] <- m
}

trainPCA <- data.frame(one = rep(NA, nrow(train)), two = rep(NA, nrow(train)))
for (i in 1:nrow(train)) {
  total1 = 0
  total2 = 0
  for(j in 1:8) {
    total1 = total1 + pcaObj$rotation[,1,drop=FALSE][j]*train[i,j+2]
    total2 = total2 + pcaObj$rotation[,2,drop=FALSE][j]*train[i,j+2]
  trainPCA[i,1] = total1
  trainPCA[i,2] = total2
  }
}

model <- lm(trainScoreDF[,1] ~ trainPCA[,1] + trainPCA[,2])

m <- rep(NA, 8)
for(i in 3:10) {
  m[i-2] <- mean(train[which(!is.na(train[,i])),i])
}
