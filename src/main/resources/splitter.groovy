List<Double[]> scores = new ArrayList<>()
def indexLine=0
new File("/Users/nicolasdesfeux/Downloads/Book2.csv").eachLine { line ->
    if(!line.startsWith(',')){
        def split = line.split(',')
        def id=split[0]
        indexLine++
        if(indexLine<=75){
            split.eachWithIndex { it, index ->
                if(index>1 && it!=null && it !=""){
                    Double[] score = new Double[4]
                    score[0] = Double.parseDouble(id)
                    score[1] = index-1
                    score[2] = Double.parseDouble(it)
                    scores.add(score)
                }
            }
        }else{
            split.eachWithIndex { it, index ->
                if(index>1 && it!=null && it !=""){
                    scores.find{ temp ->
                        temp[0] == Double.parseDouble(id) &&
                                temp[1] == index-1
                    }[3] = Double.parseDouble(it)
                }
            }
        }
    }


}

println scores.join('\n').replaceAll('\\.0,',',')